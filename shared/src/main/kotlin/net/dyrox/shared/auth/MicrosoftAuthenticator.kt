package net.dyrox.shared.auth

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import net.dyrox.shared.http.HttpService

/** What the account manager needs to keep sessions alive; faked in tests. */
interface MinecraftLoginService {
    /** Exchanges a refresh token for a fresh Minecraft session (and usually a new refresh token). */
    suspend fun refresh(refreshToken: String): MicrosoftLoginResult

    /** Loads the profile for a Minecraft access token; throws [MinecraftTokenRejectedException] if it's invalid. */
    suspend fun fetchProfile(minecraftAccessToken: String): MinecraftProfile
}

/**
 * The official Microsoft → Xbox Live → XSTS → Minecraft services chain.
 *
 * [clientId] is the Azure app ID. It must be registered as a public client ("Allow public client flows")
 * and approved by Mojang for Minecraft API access, otherwise the last step fails with [AppNotApprovedException].
 */
class MicrosoftAuthenticator(
    http: HttpService,
    val clientId: String,
    endpoints: AuthEndpoints = AuthEndpoints(),
    private val clock: () -> Long = System::currentTimeMillis,
    sleep: suspend (Long) -> Unit = { delay(it) },
) : MinecraftLoginService {
    private val oauth = MicrosoftOAuthClient(http, clientId, endpoints, clock, sleep)
    private val xbox = XboxLiveClient(http, endpoints)
    private val minecraft = MinecraftServicesClient(http, endpoints)

    init {
        require(clientId.isNotBlank()) { "An Azure client ID is required for Microsoft login" }
    }

    /** Device-code flow: [onPrompt] shows the code; this suspends until the user completes sign-in elsewhere. */
    suspend fun loginWithDeviceCode(onPrompt: (DeviceCodePrompt) -> Unit): MicrosoftLoginResult {
        val code = oauth.requestDeviceCode()
        onPrompt(DeviceCodePrompt(code.userCode, code.verificationUri, clock() + code.expiresIn * 1000))
        return completeLogin(oauth.pollDeviceCode(code), previousRefreshToken = null)
    }

    /** Browser flow with PKCE and a loopback redirect. [openBrowser] receives the sign-in URL. */
    suspend fun loginWithBrowser(openBrowser: (String) -> Unit, timeoutMillis: Long = 5 * 60_000): MicrosoftLoginResult {
        LoopbackRedirectReceiver().use { receiver ->
            val pkce = Pkce.generate()
            val state = Pkce.randomUrlSafe(16)
            openBrowser(oauth.authorizeUrl(receiver.redirectUri, pkce.challenge, state))
            val params = try {
                withTimeout(timeoutMillis) { receiver.awaitRedirect() }
            } catch (_: TimeoutCancellationException) {
                throw AuthException("Timed out waiting for the browser sign-in.")
            }
            params["error"]?.let { throw MicrosoftAuthException(it, params["error_description"]) }
            if (params["state"] != state) throw AuthException("The sign-in response didn't match this request. Try again.")
            val code = params["code"] ?: throw AuthException("The browser returned no authorization code.")
            return completeLogin(oauth.exchangeCode(code, receiver.redirectUri, pkce.verifier), previousRefreshToken = null)
        }
    }

    override suspend fun refresh(refreshToken: String): MicrosoftLoginResult =
        completeLogin(oauth.refresh(refreshToken), previousRefreshToken = refreshToken)

    override suspend fun fetchProfile(minecraftAccessToken: String): MinecraftProfile = minecraft.profile(minecraftAccessToken)

    private suspend fun completeLogin(tokens: OAuthTokenResponse, previousRefreshToken: String?): MicrosoftLoginResult {
        val refreshToken = tokens.refreshToken ?: previousRefreshToken
            ?: throw AuthException("Microsoft returned no refresh token (is offline_access allowed for this app?)")
        val xboxToken = xbox.authenticate(tokens.accessToken)
        val xsts = xbox.authorize(xboxToken.token)
        val session = minecraft.loginWithXbox(xsts.userHash, xsts.token)
        val profile = minecraft.profile(session.accessToken)
        return MicrosoftLoginResult(
            refreshToken = refreshToken,
            minecraftAccessToken = session.accessToken,
            minecraftTokenExpiresAt = clock() + session.expiresIn * 1000,
            profile = profile,
            xuid = Jwt.stringClaim(session.accessToken, "xuid"),
        )
    }
}
