package net.dyrox.shared.io

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

object AtomicFiles {
    /** Writes [bytes] to a temp file next to [target], then moves it into place, so readers never see a partial file. */
    fun write(target: Path, bytes: ByteArray) {
        target.parent?.let(Files::createDirectories)
        val temp = tempSibling(target)
        try {
            Files.write(temp, bytes)
            move(temp, target)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    fun writeString(target: Path, text: String) = write(target, text.toByteArray(Charsets.UTF_8))

    /** Moves [source] over [target], atomically where the file system supports it. */
    fun move(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun tempSibling(target: Path): Path =
        target.resolveSibling("${target.fileName}.${UUID.randomUUID().toString().take(8)}.part")

    /**
     * Resolves [relative] (a '/'-separated path from a remote document) against [base] and rejects
     * anything that would escape [base], e.g. `../../evil.dll`.
     */
    fun resolveInside(base: Path, relative: String): Path {
        val normalizedBase = base.toAbsolutePath().normalize()
        val resolved = normalizedBase.resolve(relative).normalize()
        require(resolved.startsWith(normalizedBase) && resolved != normalizedBase) {
            "Path '$relative' escapes $normalizedBase"
        }
        return resolved
    }
}
