package net.dyrox.shared.hash

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

object Hashing {
    private const val BUFFER_SIZE = 64 * 1024

    fun sha1(path: Path): String = digest(path, "SHA-1")
    fun sha1(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1").digest(bytes).toHex()
    fun sha256(path: Path): String = digest(path, "SHA-256")
    fun sha512(path: Path): String = digest(path, "SHA-512")

    fun ByteArray.toHex(): String = HexFormat.of().formatHex(this)

    private fun digest(path: Path, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }
}
