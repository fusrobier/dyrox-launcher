package net.dyrox.shared.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Microsoft identity platform (OAuth 2.0) ---

@Serializable
internal data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Long,
    val interval: Long = 5,
    val message: String? = null,
)

@Serializable
internal data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String? = null,
)

@Serializable
internal data class OAuthErrorResponse(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
)

/** Shown to the user during the device-code flow: "open [verificationUri] and enter [userCode]". */
data class DeviceCodePrompt(
    val userCode: String,
    val verificationUri: String,
    /** Epoch millis after which the code stops working. */
    val expiresAt: Long,
)

// --- Xbox Live / XSTS ---

@Serializable
internal data class XboxTokenResponse(
    @SerialName("Token") val token: String,
    @SerialName("NotAfter") val notAfter: String? = null,
    @SerialName("DisplayClaims") val displayClaims: DisplayClaims,
) {
    @Serializable
    data class DisplayClaims(val xui: List<Map<String, String>> = emptyList())

    /** The user hash, needed for the `XBL3.0 x=<uhs>;<token>` identity token. */
    val userHash: String
        get() = displayClaims.xui.firstOrNull()?.get("uhs") ?: throw AuthException("Xbox Live returned no user hash")
}

@Serializable
internal data class XboxErrorResponse(
    @SerialName("XErr") val xErr: Long? = null,
    @SerialName("Message") val message: String? = null,
)

// --- Minecraft services ---

@Serializable
internal data class MinecraftLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 86_400,
    @SerialName("token_type") val tokenType: String? = null,
)

@Serializable
data class MinecraftProfile(
    /** Undashed UUID. */
    val id: String,
    val name: String,
    val skins: List<Skin> = emptyList(),
) {
    @Serializable
    data class Skin(
        val id: String? = null,
        val state: String? = null,
        val url: String,
        val variant: String? = null,
    )

    val activeSkinUrl: String? get() = (skins.firstOrNull { it.state == "ACTIVE" } ?: skins.firstOrNull())?.url
}

/** Everything a successful Microsoft login produces; stored (encrypted) in the account vault. */
data class MicrosoftLoginResult(
    val refreshToken: String,
    val minecraftAccessToken: String,
    /** Epoch millis. */
    val minecraftTokenExpiresAt: Long,
    val profile: MinecraftProfile,
    val xuid: String?,
) {
    override fun toString(): String = "MicrosoftLoginResult(profile=${profile.name}, expiresAt=$minecraftTokenExpiresAt)"
}
