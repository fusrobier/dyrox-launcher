package net.dyrox.shared.account

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType(val label: String) {
    MICROSOFT("Microsoft"),
    OFFLINE("Offline"),
}

/** Secrets of a Microsoft account. Only ever persisted inside the encrypted vault. */
@Serializable
data class MicrosoftCredentials(
    val refreshToken: String,
    val minecraftAccessToken: String,
    /** Epoch millis. */
    val minecraftTokenExpiresAt: Long,
    val xuid: String? = null,
) {
    override fun toString(): String = "MicrosoftCredentials(expiresAt=$minecraftTokenExpiresAt)"
}

@Serializable
data class StoredAccount(
    /** Local id, independent of the Minecraft UUID. */
    val id: String,
    val type: AccountType,
    val username: String,
    /** Undashed Minecraft UUID (for offline accounts: the vanilla offline UUID). */
    val uuid: String,
    val addedAt: Long,
    val lastUsedAt: Long? = null,
    val skinUrl: String? = null,
    /** Null for offline accounts, and for Microsoft accounts that have to sign in again. */
    val microsoft: MicrosoftCredentials? = null,
)

/** The decrypted vault. */
@Serializable
data class VaultContents(
    val version: Int = 1,
    val accounts: List<StoredAccount> = emptyList(),
    val selectedAccountId: String? = null,
) {
    val selected: StoredAccount? get() = accounts.firstOrNull { it.id == selectedAccountId }
}

/** Token state as shown by the validity indicator. */
sealed interface AccountStatus {
    /** Offline profile: works in singleplayer and on offline-mode servers only. */
    data object Offline : AccountStatus

    data class Valid(val expiresAt: Long) : AccountStatus

    /** The Minecraft token expired or is about to; it is refreshed automatically on next use. */
    data object RefreshNeeded : AccountStatus

    data object SignInRequired : AccountStatus

    data class Error(val message: String) : AccountStatus
}

/** What a launch (or an in-game session swap) needs. */
data class GameSession(
    val accountId: String,
    val type: AccountType,
    val username: String,
    val uuid: String,
    val accessToken: String,
    val xuid: String?,
) {
    override fun toString(): String = "GameSession(username=$username, type=$type)"
}

/** Portable account list for import/export. Never contains tokens. */
@Serializable
data class AccountExport(
    val format: String = FORMAT,
    val version: Int = 1,
    val accounts: List<ExportedAccount>,
) {
    companion object {
        const val FORMAT = "dyrox-accounts"
    }
}

@Serializable
data class ExportedAccount(
    val type: AccountType,
    val username: String,
    val uuid: String,
)

data class ImportResult(val added: Int, val skipped: Int)
