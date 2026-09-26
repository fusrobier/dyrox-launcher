package net.dyrox.shared.account

import kotlinx.coroutines.runBlocking
import net.dyrox.shared.auth.InvalidRefreshTokenException
import net.dyrox.shared.auth.MicrosoftLoginResult
import net.dyrox.shared.auth.MinecraftLoginService
import net.dyrox.shared.auth.MinecraftProfile
import net.dyrox.shared.auth.MinecraftTokenRejectedException
import net.dyrox.shared.auth.OfflineProfiles
import net.dyrox.shared.auth.toUndashedString
import net.dyrox.shared.vault.AccountVault
import net.dyrox.shared.vault.MemoryKeyStore
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountManagerTest {
    @TempDir
    lateinit var dir: Path

    private var now = 1_000_000_000L
    private val service = FakeLoginService()
    private lateinit var manager: AccountManager

    class FakeLoginService : MinecraftLoginService {
        var refreshCalls = 0
        var revoked = false
        var rejectToken: String? = null
        var name = "Notch"

        fun login(refresh: String, expiresAt: Long) = MicrosoftLoginResult(
            refreshToken = refresh,
            minecraftAccessToken = "mc-token-for-$refresh",
            minecraftTokenExpiresAt = expiresAt,
            profile = MinecraftProfile("069a79f444e94726a5befca90e38aaf5", name),
            xuid = "2535",
        )

        override suspend fun refresh(refreshToken: String): MicrosoftLoginResult {
            if (revoked) throw InvalidRefreshTokenException()
            refreshCalls++
            return login("$refreshToken+", Long.MAX_VALUE / 2)
        }

        override suspend fun fetchProfile(minecraftAccessToken: String): MinecraftProfile {
            if (minecraftAccessToken == rejectToken) throw MinecraftTokenRejectedException()
            return MinecraftProfile("069a79f444e94726a5befca90e38aaf5", name)
        }
    }

    @BeforeTest
    fun setUp() {
        manager = newManager()
    }

    private fun newManager() = AccountManager(AccountVault(dir.resolve("accounts.vault"), keys), { service }, { now })

    private val keys = MemoryKeyStore()

    @Test
    fun `offline accounts get vanilla offline UUIDs and the first account is selected`(): Unit = runBlocking {
        val steve = manager.addOffline("Steve")
        assertEquals(OfflineProfiles.uuidFor("Steve").toUndashedString(), steve.uuid)
        assertEquals(steve.id, manager.contents.value.selectedAccountId)
        assertEquals(steve.id, manager.addOffline("Steve").id, "same offline name must not be added twice")
        assertIs<AccountStatus.Offline>(manager.status(steve))
        assertFailsWith<IllegalArgumentException> { manager.addOffline("bad name!") }
    }

    @Test
    fun `state survives a restart`(): Unit = runBlocking {
        manager.addOffline("Steve")
        manager.addMicrosoft(service.login("r1", now + 3_600_000))
        val reloaded = newManager().load()
        assertEquals(listOf("Steve", "Notch"), reloaded.accounts.map { it.username })
    }

    @Test
    fun `signing in again updates the existing Microsoft account`(): Unit = runBlocking {
        val first = manager.addMicrosoft(service.login("r1", now + 3_600_000))
        service.name = "NotchRenamed"
        val second = manager.addMicrosoft(service.login("r2", now + 3_600_000))
        assertEquals(first.id, second.id)
        assertEquals("NotchRenamed", second.username)
        assertEquals("r2", second.microsoft?.refreshToken)
        assertEquals(1, manager.contents.value.accounts.size)
    }

    @Test
    fun `removing the selected account selects another`(): Unit = runBlocking {
        val a = manager.addOffline("Alpha")
        val b = manager.addOffline("Bravo")
        manager.remove(a.id)
        assertEquals(b.id, manager.contents.value.selectedAccountId)
        manager.remove(b.id)
        assertNull(manager.contents.value.selectedAccountId)
    }

    @Test
    fun `renaming an offline account changes its UUID`(): Unit = runBlocking {
        val a = manager.addOffline("Alpha")
        val renamed = manager.renameOffline(a.id, "Charlie")
        assertEquals(OfflineProfiles.uuidFor("Charlie").toUndashedString(), renamed.uuid)
    }

    @Test
    fun `offline session uses a dummy token and marks the account used`(): Unit = runBlocking {
        val a = manager.addOffline("Alpha")
        now += 5000
        val session = manager.session(a.id)
        assertEquals("0", session.accessToken)
        assertEquals(AccountType.OFFLINE, session.type)
        assertEquals(now, manager.contents.value.accounts.single().lastUsedAt)
    }

    @Test
    fun `valid Microsoft token is used without refreshing`(): Unit = runBlocking {
        val account = manager.addMicrosoft(service.login("r1", now + 3_600_000))
        val session = manager.session(account.id)
        assertEquals("mc-token-for-r1", session.accessToken)
        assertEquals("2535", session.xuid)
        assertEquals(0, service.refreshCalls)
    }

    @Test
    fun `expiring token is refreshed and the rotated refresh token is saved`(): Unit = runBlocking {
        val account = manager.addMicrosoft(service.login("r1", now + 60_000)) // inside the 10-minute margin
        assertIs<AccountStatus.RefreshNeeded>(manager.status(account))
        val session = manager.session(account.id)
        assertEquals(1, service.refreshCalls)
        assertEquals("mc-token-for-r1+", session.accessToken)
        assertEquals("r1+", newManager().load().accounts.single().microsoft?.refreshToken)
    }

    @Test
    fun `revoked refresh token clears credentials and asks to sign in again`(): Unit = runBlocking {
        val account = manager.addMicrosoft(service.login("r1", now - 1))
        service.revoked = true
        assertFailsWith<InvalidRefreshTokenException> { manager.session(account.id) }
        val stored = manager.contents.value.accounts.single()
        assertNull(stored.microsoft)
        assertIs<AccountStatus.SignInRequired>(manager.status(stored))
    }

    @Test
    fun `live check refreshes a rejected token and picks up name changes`(): Unit = runBlocking {
        val account = manager.addMicrosoft(service.login("r1", now + 3_600_000))
        service.rejectToken = "mc-token-for-r1"
        service.name = "NewName"
        assertIs<AccountStatus.Valid>(manager.check(account.id))
        assertEquals(1, service.refreshCalls)
        assertEquals("NewName", manager.contents.value.accounts.single().username)
    }

    @Test
    fun `export never contains secrets and import skips duplicates`(): Unit = runBlocking {
        manager.addOffline("Steve")
        manager.addMicrosoft(service.login("very-secret-refresh-token", now + 3_600_000))
        val json = manager.exportJson()
        assertFalse("very-secret-refresh-token" in json)
        assertFalse("mc-token" in json)
        assertTrue("Steve" in json && "Notch" in json)

        val other = AccountManager(AccountVault(dir.resolve("other.vault"), MemoryKeyStore()), { service }, { now })
        other.addOffline("Steve")
        val result = other.importJson(json)
        assertEquals(ImportResult(added = 1, skipped = 1), result)
        val imported = other.contents.value.accounts.single { it.type == AccountType.MICROSOFT }
        assertIs<AccountStatus.SignInRequired>(other.status(imported))
        assertFailsWith<IllegalArgumentException> { other.importJson("""{"hello":"world"}""") }
    }
}
