package net.dyrox.shared.auth

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.dyrox.shared.http.HttpResult
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.json.DyroxJson
import java.net.URLEncoder

/** Service URLs; overridable so tests can point the whole chain at a local server. */
data class AuthEndpoints(
    val microsoftAuthority: String = "https://login.microsoftonline.com/consumers/oauth2/v2.0",
    val xboxUserAuthenticate: String = "https://user.auth.xboxlive.com/user/authenticate",
    val xstsAuthorize: String = "https://xsts.auth.xboxlive.com/xsts/authorize",
    val minecraftServices: String = "https://api.minecraftservices.com",
)

private fun <T> decode(deserializer: DeserializationStrategy<T>, result: HttpResult, what: String): T =
    try {
        DyroxJson.decodeFromString(deserializer, result.body)
    } catch (e: SerializationException) {
        throw AuthException("Unexpected $what response (HTTP ${result.status})", e)
    } catch (e: IllegalArgumentException) {
        throw AuthException("Unexpected $what response (HTTP ${result.status})", e)
    }

/** Step 1: Microsoft OAuth 2.0 against the `consumers` tenant (personal Microsoft accounts). */
internal class MicrosoftOAuthClient(
    private val http: HttpService,
    private val clientId: String,
    private val endpoints: AuthEndpoints,
    private val clock: () -> Long,
    private val sleep: suspend (Long) -> Unit,
) {
    suspend fun requestDeviceCode(): DeviceCodeResponse {
        val result = http.postForm("${endpoints.microsoftAuthority}/devicecode", mapOf("client_id" to clientId, "scope" to SCOPE))
        if (!result.isSuccess) throw oauthError(result)
        return decode(DeviceCodeResponse.serializer(), result, "device code")
    }

    /** Polls until the user finishes signing in on the other device (or the code expires). */
    suspend fun pollDeviceCode(code: DeviceCodeResponse): OAuthTokenResponse {
        var intervalMillis = code.interval.coerceAtLeast(1) * 1000
        val deadline = clock() + code.expiresIn * 1000
        while (true) {
            sleep(intervalMillis)
            if (clock() > deadline) throw MicrosoftAuthException("expired_token", null)
            val result = http.postForm(
                "${endpoints.microsoftAuthority}/token",
                mapOf("grant_type" to DEVICE_CODE_GRANT, "client_id" to clientId, "device_code" to code.deviceCode),
            )
            if (result.isSuccess) return decode(OAuthTokenResponse.serializer(), result, "token")
            val error = parseError(result) ?: throw oauthError(result)
            when (error.error) {
                "authorization_pending" -> Unit
                "slow_down" -> intervalMillis += 5000
                else -> throw MicrosoftAuthException(error.error, error.errorDescription)
            }
        }
    }

    fun authorizeUrl(redirectUri: String, codeChallenge: String, state: String): String {
        val query = mapOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "response_mode" to "query",
            "scope" to SCOPE,
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "prompt" to "select_account",
        ).entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, Charsets.UTF_8) }
        return "${endpoints.microsoftAuthority}/authorize?$query"
    }

    suspend fun exchangeCode(code: String, redirectUri: String, codeVerifier: String): OAuthTokenResponse {
        val result = http.postForm(
            "${endpoints.microsoftAuthority}/token",
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to clientId,
                "code" to code,
                "redirect_uri" to redirectUri,
                "code_verifier" to codeVerifier,
                "scope" to SCOPE,
            ),
        )
        if (!result.isSuccess) throw oauthError(result)
        return decode(OAuthTokenResponse.serializer(), result, "token")
    }

    suspend fun refresh(refreshToken: String): OAuthTokenResponse {
        val result = http.postForm(
            "${endpoints.microsoftAuthority}/token",
            mapOf("grant_type" to "refresh_token", "client_id" to clientId, "refresh_token" to refreshToken, "scope" to SCOPE),
        )
        if (result.isSuccess) return decode(OAuthTokenResponse.serializer(), result, "token")
        val error = parseError(result)
        if (error?.error == "invalid_grant" || error?.error == "interaction_required") throw InvalidRefreshTokenException()
        throw oauthError(result)
    }

    private fun parseError(result: HttpResult): OAuthErrorResponse? =
        runCatching { DyroxJson.decodeFromString(OAuthErrorResponse.serializer(), result.body) }.getOrNull()

    private fun oauthError(result: HttpResult): AuthException =
        parseError(result)?.let { MicrosoftAuthException(it.error, it.errorDescription) }
            ?: AuthException("Microsoft sign-in failed (HTTP ${result.status})")

    companion object {
        const val SCOPE = "XboxLive.signin offline_access"
        const val DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
    }
}

/** Steps 2 and 3: Xbox Live user token, then an XSTS token for Minecraft services. */
internal class XboxLiveClient(
    private val http: HttpService,
    private val endpoints: AuthEndpoints,
) {
    suspend fun authenticate(microsoftAccessToken: String): XboxTokenResponse {
        val body = buildJsonObject {
            put("Properties", buildJsonObject {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", "d=$microsoftAccessToken")
            })
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }
        val result = http.postJson(endpoints.xboxUserAuthenticate, body.toString(), mapOf("x-xbl-contract-version" to "1"))
        if (!result.isSuccess) throw xboxError(result, "Xbox Live")
        return decode(XboxTokenResponse.serializer(), result, "Xbox Live")
    }

    suspend fun authorize(xboxToken: String): XboxTokenResponse {
        val body = buildJsonObject {
            put("Properties", buildJsonObject {
                put("SandboxId", "RETAIL")
                put("UserTokens", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(xboxToken)) })
            })
            put("RelyingParty", MINECRAFT_RELYING_PARTY)
            put("TokenType", "JWT")
        }
        val result = http.postJson(endpoints.xstsAuthorize, body.toString())
        if (!result.isSuccess) throw xboxError(result, "XSTS")
        return decode(XboxTokenResponse.serializer(), result, "XSTS")
    }

    private fun xboxError(result: HttpResult, stage: String): XboxAuthException {
        val xErr = runCatching { DyroxJson.decodeFromString(XboxErrorResponse.serializer(), result.body).xErr }.getOrNull()
        return if (xErr != null) XboxAuthException.fromXErr(xErr) else XboxAuthException(null, "$stage sign-in failed (HTTP ${result.status})")
    }

    companion object {
        const val MINECRAFT_RELYING_PARTY = "rp://api.minecraftservices.com/"
    }
}

/** Step 4: Minecraft services token and profile. */
internal class MinecraftServicesClient(
    private val http: HttpService,
    private val endpoints: AuthEndpoints,
) {
    suspend fun loginWithXbox(userHash: String, xstsToken: String): MinecraftLoginResponse {
        val body = buildJsonObject { put("identityToken", "XBL3.0 x=$userHash;$xstsToken") }
        val result = http.postJson("${endpoints.minecraftServices}/authentication/login_with_xbox", body.toString())
        return when {
            result.isSuccess -> decode(MinecraftLoginResponse.serializer(), result, "Minecraft login")
            result.status == 403 && result.body.contains("Invalid app registration", ignoreCase = true) -> throw AppNotApprovedException()
            else -> throw statusError(result, "Minecraft sign-in")
        }
    }

    suspend fun profile(accessToken: String): MinecraftProfile {
        val result = http.get("${endpoints.minecraftServices}/minecraft/profile", mapOf("Authorization" to "Bearer $accessToken"))
        return when (result.status) {
            in 200..299 -> decode(MinecraftProfile.serializer(), result, "Minecraft profile")
            404 -> throw NoMinecraftProfileException()
            401 -> throw MinecraftTokenRejectedException()
            else -> throw statusError(result, "Loading the Minecraft profile")
        }
    }

    private fun statusError(result: HttpResult, what: String): MinecraftAuthException = when (result.status) {
        429 -> MinecraftAuthException(429, "Too many sign-in attempts. Wait a minute and try again.")
        else -> MinecraftAuthException(result.status, "$what failed (HTTP ${result.status}).")
    }
}
