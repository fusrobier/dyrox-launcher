package net.dyrox.shared.vault

import net.dyrox.shared.account.AccountType
import net.dyrox.shared.account.MicrosoftCredentials
import net.dyrox.shared.account.StoredAccount
import net.dyrox.shared.account.VaultContents
import net.dyrox.shared.platform.OperatingSystem
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Keeps the key in memory, like a keyring would. */
class MemoryKeyStore(var key: ByteArray? = null) : MasterKeyStore {
    override val description = "memory"
    override fun load(): ByteArray? = key
    override fun save(key: ByteArray) {
        this.key = key
    }
}

class AccountVaultTest {
    @TempDir
    lateinit var dir: Path

    private val secret = "super-secret-refresh-token-0123456789"

    private fun account(name: String) = StoredAccount(
        id = name, type = AccountType.MICROSOFT, username = name, uuid = "0".repeat(32), addedAt = 1,
        microsoft = MicrosoftCredentials(secret, "mc-token-$name", 42),
    )

    @Test
    fun `round-trips accounts and never stores them in plaintext`() {
        val keys = MemoryKeyStore()
        val vault = AccountVault(dir.resolve("accounts.vault"), keys)
        vault.update { it.copy(accounts = listOf(account("Alice")), selectedAccountId = "Alice") }

        val reopened = AccountVault(dir.resolve("accounts.vault"), keys).read()
        assertEquals("Alice", reopened.selected?.username)
        assertEquals(secret, reopened.accounts.single().microsoft?.refreshToken)

        val raw = Files.readAllBytes(dir.resolve("accounts.vault"))
        assertEquals("DYRXVLT1", raw.copyOfRange(0, 8).toString(Charsets.US_ASCII))
        val text = raw.toString(Charsets.ISO_8859_1)
        assertFalse(secret in text, "refresh token leaked in plaintext")
        assertFalse("Alice" in text, "account name leaked in plaintext")
    }

    @Test
    fun `missing vault reads as empty`() {
        assertEquals(VaultContents(), AccountVault(dir.resolve("none.vault"), MemoryKeyStore()).read())
    }

    @Test
    fun `tampering is detected`() {
        val keys = MemoryKeyStore()
        val file = dir.resolve("accounts.vault")
        AccountVault(file, keys).update { it.copy(accounts = listOf(account("Alice"))) }
        val bytes = Files.readAllBytes(file)
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        Files.write(file, bytes)
        assertFailsWith<VaultCorruptedException> { AccountVault(file, keys).read() }
    }

    @Test
    fun `a different key cannot open the vault`() {
        val file = dir.resolve("accounts.vault")
        AccountVault(file, MemoryKeyStore()).update { it.copy(accounts = listOf(account("Alice"))) }
        assertFailsWith<VaultCorruptedException> { AccountVault(file, MemoryKeyStore(ByteArray(32) { 7 })).read() }
    }

    @Test
    fun `an existing vault is never silently replaced when its key is gone`() {
        val file = dir.resolve("accounts.vault")
        AccountVault(file, MemoryKeyStore()).update { it.copy(accounts = listOf(account("Alice"))) }
        val lostKey = MemoryKeyStore()
        assertFailsWith<VaultLockedException> { AccountVault(file, lostKey).read() }
        assertEquals(null, lostKey.key, "no new key may be created for an existing vault")

        val backup = AccountVault(file, lostKey).moveAside()
        assertNotNull(backup)
        assertTrue(Files.exists(backup))
        assertEquals(VaultContents(), AccountVault(file, lostKey).read())
    }

    @Test
    fun `concurrent updates are all kept`() {
        val vault = AccountVault(dir.resolve("accounts.vault"), MemoryKeyStore())
        (1..20).map { i -> thread { vault.update { it.copy(accounts = it.accounts + account("user$i")) } } }.forEach { it.join() }
        assertEquals(20, vault.read().accounts.size)
    }

    @Test
    fun `file key store persists the key`() {
        val store = FileKeyStore(dir.resolve("vault.key"))
        assertEquals(null, store.load())
        store.save(ByteArray(32) { it.toByte() })
        assertContentEquals(ByteArray(32) { it.toByte() }, FileKeyStore(dir.resolve("vault.key")).load())
    }

    @Test
    fun `DPAPI protects and restores the key`() {
        assumeTrue(OperatingSystem.current == OperatingSystem.WINDOWS, "DPAPI is Windows-only")
        val file = dir.resolve("vault.key.dpapi")
        val key = ByteArray(32) { (it * 3).toByte() }
        DpapiKeyStore(file).save(key)
        assertFalse(Files.readAllBytes(file).toList().windowed(32).any { it == key.toList() }, "key stored unprotected")
        assertContentEquals(key, DpapiKeyStore(file).load())
    }
}
