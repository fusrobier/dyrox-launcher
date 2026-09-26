package net.dyrox.shared.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import net.dyrox.shared.auth.AuthException
import net.dyrox.shared.auth.InvalidRefreshTokenException
import net.dyrox.shared.auth.MicrosoftLoginResult
import net.dyrox.shared.auth.MinecraftLoginService
import net.dyrox.shared.auth.MinecraftTokenRejectedException
import net.dyrox.shared.auth.OfflineProfiles
import net.dyrox.shared.auth.toUndashedString
import net.dyrox.shared.json.DyroxJsonPretty
import net.dyrox.shared.vault.AccountVault
import java.io.IOException
import java.util.UUID

class AccountNotFoundException(id: String) : NoSuchElementException("No account with id $id")

/**
 * All account operations. State lives in the encrypted [vault]; [contents] mirrors it for the UI.
 * Every mutation is serialised by [mutex], and inside the vault by a cross-process file lock.
 *
 * [loginService] is resolved on each use because the Azure client ID can change in Settings;
 * it's null while none is configured.
 */
class AccountManager(
    private val vault: AccountVault,
    private val loginService: () -> MinecraftLoginService?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _contents = MutableStateFlow(VaultContents())

    val contents: StateFlow<VaultContents> = _contents.asStateFlow()
    val keyProtection: String get() = vault.keyProtection
    val microsoftLoginAvailable: Boolean get() = loginService() != null

    suspend fun load(): VaultContents = mutex.withLock {
        io { vault.read() }.also { _contents.value = it }
    }

    /** Recovery for an unreadable vault: moves it aside (kept as a backup) and starts empty. */
    suspend fun moveVaultAside(): java.nio.file.Path? = mutex.withLock {
        io { vault.moveAside() }.also { _contents.value = VaultContents() }
    }

    suspend fun addOffline(username: String): StoredAccount = mutex.withLock {
        require(OfflineProfiles.isValidName(username)) { "Names must be 3-16 characters: letters, digits and _" }
        val uuid = OfflineProfiles.uuidFor(username).toUndashedString()
        mutateLocked { contents ->
            contents.accounts.firstOrNull { it.type == AccountType.OFFLINE && it.uuid == uuid }?.let { return@mutateLocked contents to it }
            val account = StoredAccount(newId(), AccountType.OFFLINE, username, uuid, addedAt = clock())
            contents.withAdded(account) to account
        }
    }

    /** Adds a freshly signed-in Microsoft account, or refreshes the stored one with the same UUID. */
    suspend fun addMicrosoft(login: MicrosoftLoginResult): StoredAccount = mutex.withLock {
        mutateLocked { contents ->
            val existing = contents.accounts.firstOrNull { it.type == AccountType.MICROSOFT && it.uuid == login.profile.id }
            if (existing != null) {
                val updated = existing.withLogin(login)
                contents.replace(updated) to updated
            } else {
                val account = StoredAccount(newId(), AccountType.MICROSOFT, login.profile.name, login.profile.id, addedAt = clock())
                    .withLogin(login)
                contents.withAdded(account) to account
            }
        }
    }

    /** Offline accounts only: a new name also means a new offline UUID. */
    suspend fun renameOffline(id: String, newName: String): StoredAccount = mutex.withLock {
        require(OfflineProfiles.isValidName(newName)) { "Names must be 3-16 characters: letters, digits and _" }
        mutateLocked { contents ->
            val account = contents.find(id)
            require(account.type == AccountType.OFFLINE) { "Only offline accounts can be renamed" }
            val updated = account.copy(username = newName, uuid = OfflineProfiles.uuidFor(newName).toUndashedString())
            contents.replace(updated) to updated
        }
    }

    suspend fun remove(id: String): Unit = mutex.withLock {
        mutateLocked { contents ->
            val remaining = contents.accounts.filterNot { it.id == id }
            val selected = contents.selectedAccountId.takeIf { it != id } ?: remaining.firstOrNull()?.id
            contents.copy(accounts = remaining, selectedAccountId = selected) to Unit
        }
    }

    suspend fun select(id: String): Unit = mutex.withLock {
        mutateLocked { contents -> contents.find(id).let { contents.copy(selectedAccountId = it.id) } to Unit }
    }

    /** Status from stored data only (no network), for list rendering. */
    fun status(account: StoredAccount): AccountStatus {
        if (account.type == AccountType.OFFLINE) return AccountStatus.Offline
        val credentials = account.microsoft ?: return AccountStatus.SignInRequired
        return if (credentials.minecraftTokenExpiresAt - clock() > REFRESH_MARGIN_MILLIS) {
            AccountStatus.Valid(credentials.minecraftTokenExpiresAt)
        } else {
            AccountStatus.RefreshNeeded
        }
    }

    /**
     * A ready-to-use session for [id]: refreshes the Minecraft token if it's about to expire and marks
     * the account as used. Throws [InvalidRefreshTokenException] if the user must sign in again.
     */
    suspend fun session(id: String): GameSession = mutex.withLock {
        var account = io { vault.read() }.find(id)
        if (account.type == AccountType.MICROSOFT) {
            val credentials = account.microsoft ?: throw InvalidRefreshTokenException()
            if (credentials.minecraftTokenExpiresAt - clock() <= REFRESH_MARGIN_MILLIS) account = refreshLocked(account)
        }
        // Re-read inside the update so fresh credentials from the refresh above are kept.
        account = mutateLocked { contents ->
            val used = contents.find(id).copy(lastUsedAt = clock())
            contents.replace(used) to used
        }
        GameSession(
            accountId = account.id,
            type = account.type,
            username = account.username,
            uuid = account.uuid,
            // Offline sessions need a non-empty token; online servers reject it, as intended.
            accessToken = account.microsoft?.minecraftAccessToken ?: "0",
            xuid = account.microsoft?.xuid,
        )
    }

    /** Live validity check: asks Minecraft services whether the token works, refreshing it if needed. */
    suspend fun check(id: String): AccountStatus = mutex.withLock {
        var account = io { vault.read() }.find(id)
        if (account.type == AccountType.OFFLINE) return@withLock AccountStatus.Offline
        val credentials = account.microsoft ?: return@withLock AccountStatus.SignInRequired
        val service = loginService() ?: return@withLock AccountStatus.Error("Microsoft login isn't configured (Settings → Azure client ID)")
        try {
            if (credentials.minecraftTokenExpiresAt - clock() <= REFRESH_MARGIN_MILLIS) account = refreshLocked(account)
            val profile = try {
                service.fetchProfile(account.microsoft!!.minecraftAccessToken)
            } catch (_: MinecraftTokenRejectedException) {
                account = refreshLocked(account)
                service.fetchProfile(account.microsoft!!.minecraftAccessToken)
            }
            if (profile.name != account.username || profile.activeSkinUrl != account.skinUrl) {
                val current = account
                mutateLocked { contents ->
                    val updated = contents.find(current.id).copy(username = profile.name, skinUrl = profile.activeSkinUrl)
                    contents.replace(updated) to updated
                }
            }
            status(account)
        } catch (_: InvalidRefreshTokenException) {
            AccountStatus.SignInRequired
        } catch (e: AuthException) {
            AccountStatus.Error(e.message ?: "Sign-in check failed")
        } catch (e: IOException) {
            AccountStatus.Error("Network error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** JSON with names, UUIDs and types only; tokens are never exported. */
    fun exportJson(): String {
        val export = AccountExport(accounts = _contents.value.accounts.map { ExportedAccount(it.type, it.username, it.uuid) })
        return DyroxJsonPretty.encodeToString(AccountExport.serializer(), export)
    }

    /** Imported Microsoft accounts have no tokens, so they show "sign in required" until the user signs in. */
    suspend fun importJson(json: String): ImportResult = mutex.withLock {
        val export = try {
            DyroxJsonPretty.decodeFromString(AccountExport.serializer(), json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Not a Dyrox account export", e)
        }
        require(export.format == AccountExport.FORMAT) { "Not a Dyrox account export" }
        mutateLocked { contents ->
            var updated = contents
            var added = 0
            for (entry in export.accounts) {
                val duplicate = updated.accounts.any { it.type == entry.type && it.uuid.equals(entry.uuid, ignoreCase = true) }
                val valid = entry.uuid.matches(UUID_PATTERN) &&
                    (entry.type == AccountType.MICROSOFT || OfflineProfiles.isValidName(entry.username))
                if (duplicate || !valid) continue
                updated = updated.withAdded(StoredAccount(newId(), entry.type, entry.username, entry.uuid.lowercase(), addedAt = clock()))
                added++
            }
            updated to ImportResult(added, export.accounts.size - added)
        }
    }

    private suspend fun refreshLocked(account: StoredAccount): StoredAccount {
        val service = loginService() ?: throw AuthException("Microsoft login isn't configured (Settings → Azure client ID)")
        val credentials = account.microsoft ?: throw InvalidRefreshTokenException()
        val login = try {
            service.refresh(credentials.refreshToken)
        } catch (e: InvalidRefreshTokenException) {
            mutateLocked { contents -> contents.replace(contents.find(account.id).copy(microsoft = null)) to Unit }
            throw e
        }
        return mutateLocked { contents ->
            val updated = contents.find(account.id).withLogin(login)
            contents.replace(updated) to updated
        }
    }

    /** Must be called with [mutex] held. */
    private suspend fun <T> mutateLocked(block: (VaultContents) -> Pair<VaultContents, T>): T = io {
        lateinit var outcome: Pair<VaultContents, T>
        val saved = vault.update { current -> block(current).also { outcome = it }.first }
        _contents.value = saved
        outcome.second
    }

    private fun StoredAccount.withLogin(login: MicrosoftLoginResult) = copy(
        username = login.profile.name,
        uuid = login.profile.id,
        skinUrl = login.profile.activeSkinUrl,
        microsoft = MicrosoftCredentials(login.refreshToken, login.minecraftAccessToken, login.minecraftTokenExpiresAt, login.xuid),
    )

    private fun VaultContents.find(id: String): StoredAccount = accounts.firstOrNull { it.id == id } ?: throw AccountNotFoundException(id)

    private fun VaultContents.replace(account: StoredAccount) = copy(accounts = accounts.map { if (it.id == account.id) account else it })

    private fun VaultContents.withAdded(account: StoredAccount) =
        copy(accounts = accounts + account, selectedAccountId = selectedAccountId ?: account.id)

    private fun newId(): String = UUID.randomUUID().toString()

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        /** Refresh Minecraft tokens this long before they expire, so a session never dies mid-launch. */
        const val REFRESH_MARGIN_MILLIS = 10 * 60_000L
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{32}$")
    }
}
