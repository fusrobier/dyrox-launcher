package net.dyrox.shared.vault

import com.sun.jna.platform.win32.Crypt32Util
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.platform.OperatingSystem
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Where the vault's 256-bit master key lives. The key itself never touches disk unprotected (except [FileKeyStore]). */
interface MasterKeyStore {
    /** Shown in the UI, e.g. "Windows DPAPI". */
    val description: String

    /** The stored key, or null if none was created yet. */
    fun load(): ByteArray?

    fun save(key: ByteArray)
}

object MasterKeyStores {
    /**
     * Windows: DPAPI. Linux: Secret Service (GNOME Keyring / KWallet via `secret-tool`) when available,
     * otherwise an owner-only key file. Once a store has been used it keeps being used.
     */
    fun forPlatform(dir: Path, os: OperatingSystem = OperatingSystem.current): MasterKeyStore {
        val file = FileKeyStore(dir.resolve("vault.key"))
        return when {
            os == OperatingSystem.WINDOWS && DpapiKeyStore.isAvailable() -> DpapiKeyStore(dir.resolve("vault.key.dpapi"))
            os == OperatingSystem.LINUX -> {
                val secretService = SecretServiceKeyStore(dir.resolve("vault.key.secret-service"))
                when {
                    secretService.isInUse -> secretService
                    file.exists -> file
                    SecretServiceKeyStore.isAvailable() -> PreferredKeyStore(secretService, file)
                    else -> file
                }
            }
            else -> file
        }
    }
}

/** Windows Data Protection API: the key can only be decrypted by the same Windows user. */
class DpapiKeyStore(private val file: Path) : MasterKeyStore {
    override val description = "Windows DPAPI (tied to your Windows user account)"

    override fun load(): ByteArray? {
        if (!Files.isRegularFile(file)) return null
        return Crypt32Util.cryptUnprotectData(Files.readAllBytes(file), ENTROPY, CRYPTPROTECT_UI_FORBIDDEN, null)
    }

    override fun save(key: ByteArray) {
        AtomicFiles.writePrivate(file, Crypt32Util.cryptProtectData(key, ENTROPY, CRYPTPROTECT_UI_FORBIDDEN, "Dyrox Launcher vault key", null))
    }

    companion object {
        /** Extra entropy so other programs running as the same user can't unprotect the blob by accident. */
        private val ENTROPY = "dyrox-vault-v1".toByteArray(Charsets.US_ASCII)
        private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

        fun isAvailable(): Boolean = runCatching { Class.forName("com.sun.jna.platform.win32.Crypt32Util") }.isSuccess
    }
}

/** Linux desktop keyring through the `secret-tool` CLI (package `libsecret-tools` / `libsecret`). */
class SecretServiceKeyStore(private val marker: Path) : MasterKeyStore {
    override val description = "Secret Service keyring (GNOME Keyring / KWallet)"

    /** Set once a key was stored in the keyring, so a temporarily locked keyring never leads to a new key. */
    val isInUse: Boolean get() = Files.exists(marker)

    override fun load(): ByteArray? {
        val (exit, output) = run(listOf("secret-tool", "lookup", "service", SERVICE, "key", KEY_NAME), stdin = null)
        if (exit != 0 || output.isBlank()) return null
        return Base64.getDecoder().decode(output.trim())
    }

    override fun save(key: ByteArray) {
        val (exit, _) = run(
            listOf("secret-tool", "store", "--label=Dyrox Launcher account vault", "service", SERVICE, "key", KEY_NAME),
            stdin = Base64.getEncoder().encodeToString(key),
        )
        if (exit != 0) throw IOException("secret-tool store failed with exit code $exit")
        AtomicFiles.writeString(marker, "The Dyrox vault key is stored in the Secret Service keyring.\n")
    }

    private fun run(command: List<String>, stdin: String?): Pair<Int, String> {
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
        process.outputStream.use { out -> stdin?.let { out.write(it.toByteArray(Charsets.UTF_8)) } }
        val output = process.inputStream.bufferedReader().readText()
        // Generous timeout: unlocking the keyring may show a password prompt.
        if (!process.waitFor(2, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw IOException("secret-tool timed out")
        }
        return process.exitValue() to output
    }

    companion object {
        private const val SERVICE = "dyrox-launcher"
        private const val KEY_NAME = "vault-master-key"

        fun isAvailable(): Boolean = System.getenv("PATH").orEmpty().split(java.io.File.pathSeparatorChar)
            .any { it.isNotBlank() && Files.isExecutable(Path.of(it, "secret-tool")) }
    }
}

/** Fallback: a random key in an owner-only file. Protects against copying the vault alone, not against malware running as you. */
class FileKeyStore(private val file: Path) : MasterKeyStore {
    override val description = "Local key file (protected by file permissions only)"

    val exists: Boolean get() = Files.isRegularFile(file)

    override fun load(): ByteArray? = if (exists) Files.readAllBytes(file) else null

    override fun save(key: ByteArray) = AtomicFiles.writePrivate(file, key)
}

/** Uses [primary] for new keys, falling back to [fallback] if [primary] can't store one (e.g. no keyring daemon). */
class PreferredKeyStore(private val primary: MasterKeyStore, private val fallback: MasterKeyStore) : MasterKeyStore {
    private var active: MasterKeyStore = primary

    override val description: String get() = active.description

    override fun load(): ByteArray? = primary.load() ?: fallback.load()?.also { active = fallback }

    override fun save(key: ByteArray) {
        try {
            primary.save(key)
            active = primary
        } catch (_: Exception) {
            fallback.save(key)
            active = fallback
        }
    }
}
