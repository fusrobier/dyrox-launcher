package net.dyrox.shared.vault

import kotlinx.serialization.SerializationException
import net.dyrox.shared.account.VaultContents
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.json.DyroxJson
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.withLock

/** The vault file exists but can't be decrypted (tampered with, truncated, or encrypted with another key). */
class VaultCorruptedException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The vault file exists but its key is gone (e.g. copied to another PC or Windows user). */
class VaultLockedException(message: String) : IOException(message)

/**
 * Encrypted account storage: `accounts.vault` = magic + 12-byte nonce + AES-256-GCM(JSON).
 * GCM authenticates the data, so any modification is detected on read. Access is serialised across
 * threads and processes (several game instances and the launcher may touch it) with a lock file.
 */
class AccountVault(
    private val file: Path,
    private val keyStore: MasterKeyStore,
) {
    private val lockFile: Path = file.resolveSibling("${file.fileName}.lock")
    private val jvmLock: ReentrantLock = JVM_LOCKS.computeIfAbsent(file.toAbsolutePath().normalize()) { ReentrantLock() }
    private var cachedKey: ByteArray? = null

    val keyProtection: String get() = keyStore.description

    fun read(): VaultContents = locked { readUnlocked() }

    /** Read-modify-write under the lock; returns what was saved. */
    fun update(transform: (VaultContents) -> VaultContents): VaultContents = locked {
        val updated = transform(readUnlocked())
        val plaintext = DyroxJson.encodeToString(VaultContents.serializer(), updated).toByteArray(Charsets.UTF_8)
        AtomicFiles.writePrivate(file, VaultCrypto.encrypt(key(), plaintext))
        updated
    }

    /** Moves an unreadable vault aside (never deletes it) so a fresh one can be started. Returns the backup path. */
    fun moveAside(): Path? = locked {
        if (!Files.exists(file)) return@locked null
        val backup = file.resolveSibling("${file.fileName}.unreadable-${System.currentTimeMillis()}")
        Files.move(file, backup)
        cachedKey = null
        backup
    }

    private fun readUnlocked(): VaultContents {
        if (!Files.isRegularFile(file)) return VaultContents()
        val plaintext = VaultCrypto.decrypt(key(), Files.readAllBytes(file))
        return try {
            DyroxJson.decodeFromString(VaultContents.serializer(), plaintext.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw VaultCorruptedException("The account vault decrypted but its contents are invalid", e)
        }
    }

    private fun key(): ByteArray {
        cachedKey?.let { return it }
        val stored = try {
            keyStore.load()
        } catch (e: Exception) {
            throw VaultLockedException("Could not unlock the account vault key (${keyStore.description}): ${e.message}")
        }
        val key = when {
            stored != null -> stored
            Files.exists(file) -> throw VaultLockedException(
                "The account vault exists but its key is missing (${keyStore.description}). " +
                    "Vaults can't be moved to another PC or user account.",
            )
            else -> ByteArray(KEY_BYTES).also(RANDOM::nextBytes).also(keyStore::save)
        }
        if (key.size != KEY_BYTES) throw VaultLockedException("The account vault key has an invalid length")
        cachedKey = key
        return key
    }

    private inline fun <T> locked(block: () -> T): T = jvmLock.withLock {
        lockFile.parent?.let(Files::createDirectories)
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { block() }
        }
    }

    private companion object {
        const val KEY_BYTES = 32
        val RANDOM = SecureRandom()
        val JVM_LOCKS = ConcurrentHashMap<Path, ReentrantLock>()
    }
}

internal object VaultCrypto {
    private val MAGIC = "DYRXVLT1".toByteArray(Charsets.US_ASCII)
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(MAGIC)
        return MAGIC + nonce + cipher.doFinal(plaintext)
    }

    fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
        if (data.size < MAGIC.size + NONCE_BYTES + TAG_BITS / 8 || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw VaultCorruptedException("Not a Dyrox account vault, or the file is truncated")
        }
        val nonce = data.copyOfRange(MAGIC.size, MAGIC.size + NONCE_BYTES)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(MAGIC)
            cipher.doFinal(data, MAGIC.size + NONCE_BYTES, data.size - MAGIC.size - NONCE_BYTES)
        } catch (e: AEADBadTagException) {
            throw VaultCorruptedException("The account vault was modified or belongs to a different key", e)
        } catch (e: GeneralSecurityException) {
            throw VaultCorruptedException("The account vault could not be decrypted", e)
        }
    }
}
