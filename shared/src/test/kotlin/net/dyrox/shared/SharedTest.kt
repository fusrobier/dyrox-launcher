package net.dyrox.shared

import net.dyrox.shared.auth.OfflineProfiles
import net.dyrox.shared.auth.toUndashedString
import net.dyrox.shared.hash.Hashing
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.platform.Architecture
import net.dyrox.shared.platform.OperatingSystem
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedTest {
    @Test
    fun `sha1 matches the FIPS test vector`() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Hashing.sha1("abc".toByteArray()))
    }

    @Test
    fun `sha1 of a file equals sha1 of its bytes`(@TempDir dir: Path) {
        val file = dir.resolve("f.bin")
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        Files.write(file, bytes)
        assertEquals(Hashing.sha1(bytes), Hashing.sha1(file))
    }

    @Test
    fun `offline uuid matches what vanilla offline servers assign`() {
        // Well-known value: the offline-mode UUID of "Notch".
        assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", OfflineProfiles.uuidFor("Notch").toString())
        assertEquals("b50ad385829d3141a2167e7d7539ba7f", OfflineProfiles.uuidFor("Notch").toUndashedString())
    }

    @Test
    fun `username validation follows Minecraft rules`() {
        assertTrue(OfflineProfiles.isValidName("Steve_123"))
        assertFalse(OfflineProfiles.isValidName("ab"))
        assertFalse(OfflineProfiles.isValidName("way_too_long_name_x"))
        assertFalse(OfflineProfiles.isValidName("bad name"))
    }

    @Test
    fun `platform detection maps JVM names to Mojang names`() {
        assertEquals(OperatingSystem.WINDOWS, OperatingSystem.fromOsName("Windows 11"))
        assertEquals(OperatingSystem.LINUX, OperatingSystem.fromOsName("Linux"))
        assertEquals(OperatingSystem.MACOS, OperatingSystem.fromOsName("Mac OS X"))
        assertEquals(Architecture.X86_64, Architecture.fromOsArch("amd64"))
        assertEquals(Architecture.ARM64, Architecture.fromOsArch("aarch64"))
        assertEquals(Architecture.X86, Architecture.fromOsArch("x86"))
    }

    @Test
    fun `resolveInside rejects path traversal`(@TempDir dir: Path) {
        assertEquals(dir.resolve("a/b.jar").normalize(), AtomicFiles.resolveInside(dir, "a/b.jar"))
        assertFailsWith<IllegalArgumentException> { AtomicFiles.resolveInside(dir, "../evil.dll") }
        assertFailsWith<IllegalArgumentException> { AtomicFiles.resolveInside(dir, "a/../../evil.dll") }
    }

    @Test
    fun `atomic write replaces content and leaves no temp files`(@TempDir dir: Path) {
        val target = dir.resolve("sub/file.txt")
        AtomicFiles.writeString(target, "one")
        AtomicFiles.writeString(target, "two")
        assertEquals("two", Files.readString(target))
        assertEquals(listOf("file.txt"), Files.list(target.parent).use { s -> s.map { it.fileName.toString() }.toList() })
    }
}
