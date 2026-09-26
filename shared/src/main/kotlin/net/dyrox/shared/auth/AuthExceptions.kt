package net.dyrox.shared.auth

/** Base class for login failures. [message] is always safe and meant to be shown to the user. */
open class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** An OAuth error returned by Microsoft's identity platform (`error` / `error_description`). */
class MicrosoftAuthException(val error: String, description: String?) :
    AuthException(describe(error, description)) {
    private companion object {
        fun describe(error: String, description: String?): String = when (error) {
            "authorization_declined", "access_denied" -> "The sign-in was cancelled in the browser."
            "expired_token" -> "The sign-in code expired. Start again."
            "invalid_client", "unauthorized_client" ->
                "Microsoft rejected the client ID. Check the Azure app ID in Settings and that public client flows are enabled."
            else -> "Microsoft sign-in failed: $error" + description?.lineSequence()?.firstOrNull()?.let { " ($it)" }.orEmpty()
        }
    }
}

/** The stored refresh token no longer works (revoked, password changed, expired). The user must sign in again. */
class InvalidRefreshTokenException : AuthException("This account's sign-in has expired. Sign in again.")

/** Xbox Live / XSTS refused the account. [xErr] is Microsoft's XErr code, when given. */
class XboxAuthException(val xErr: Long?, message: String) : AuthException(message) {
    companion object {
        fun fromXErr(xErr: Long): XboxAuthException = XboxAuthException(xErr, describe(xErr))

        fun describe(xErr: Long): String = when (xErr) {
            2148916227 -> "This account is banned from Xbox services."
            2148916229 -> "This account needs a parent's permission to play online (Microsoft family settings)."
            2148916233 -> "This Microsoft account has no Xbox profile yet. Sign in once at xbox.com or minecraft.net, then try again."
            2148916235 -> "Xbox Live isn't available in this account's country or region."
            2148916236, 2148916237 -> "This account needs adult verification on xbox.com (South Korea) before it can play."
            2148916238 -> "This is a child account. An adult must add it to a Microsoft family before it can play."
            else -> "Xbox Live sign-in failed (XErr $xErr)."
        }
    }
}

/** Minecraft services returned an error. */
open class MinecraftAuthException(val status: Int, message: String) : AuthException(message)

/** HTTP 403 from `login_with_xbox`: the Azure app ID hasn't been approved by Mojang for Minecraft API access. */
class AppNotApprovedException : MinecraftAuthException(
    403,
    "Mojang hasn't approved this Azure app ID for Minecraft sign-in yet (\"Invalid app registration\"). " +
        "Request access for the app, or use an offline account until it's approved.",
)

/** The Microsoft account works but owns no Minecraft: Java Edition profile. */
class NoMinecraftProfileException : MinecraftAuthException(
    404,
    "This Microsoft account doesn't own Minecraft: Java Edition, or hasn't created a profile name yet.",
)

/** A Minecraft access token was rejected (HTTP 401). */
class MinecraftTokenRejectedException : MinecraftAuthException(401, "The Minecraft session token was rejected.")
