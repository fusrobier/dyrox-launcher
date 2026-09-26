package net.dyrox.launcher.core.instance

import net.dyrox.launcher.core.LauncherPaths
import net.dyrox.launcher.core.process.LogLevel
import net.dyrox.launcher.core.process.LogLine
import net.dyrox.launcher.core.process.LogSource
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InstanceRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var paths: LauncherPaths
    private lateinit var repo: InstanceRepository

    @BeforeTest
    fun setUp() {
        paths = LauncherPaths(dir)
        repo = InstanceRepository(paths, clock = { 1000L }, moveToTrash = { false })
        repo.load()
    }

    @Test
    fun `ids are folder-safe and unique`() {
        assertEquals("my-pvp", repo.create("My PvP!", "26.3", LoaderType.FABRIC).id)
        assertEquals("my-pvp-2", repo.create("My PvP!", "26.3", LoaderType.FABRIC).id)
        assertEquals("instance", repo.create("!!!", "26.3", LoaderType.VANILLA).id)
    }

    @Test
    fun `instances survive a reload`() {
        val created = repo.save(repo.create("Main", "26.3", LoaderType.FABRIC).copy(maxMemoryMb = 6144, jvmArguments = listOf("-XX:+UseZGC")))
        val reloaded = InstanceRepository(paths).load().single()
        assertEquals(created, reloaded)
    }

    @Test
    fun `two instances can never share a game folder`() {
        val a = repo.create("A", "26.3", LoaderType.FABRIC)
        val b = repo.create("B", "26.3", LoaderType.FABRIC)
        assertNotEquals(repo.gameDirectory(a), repo.gameDirectory(b))

        val shared = dir.resolve("custom-game-dir").toString()
        repo.save(a.copy(gameDirectory = shared))
        assertFailsWith<InstanceConflictException> { repo.save(b.copy(gameDirectory = shared)) }
        // Nested folders would also share files.
        assertFailsWith<InstanceConflictException> { repo.save(b.copy(gameDirectory = "$shared/inner")) }
    }

    @Test
    fun `duplicates copy player settings but get their own folder`() {
        val a = repo.create("A", "26.3", LoaderType.FABRIC)
        Files.createDirectories(repo.gameDirectory(a))
        Files.writeString(repo.gameDirectory(a).resolve("options.txt"), "fov:0.5")
        val copy = repo.duplicate(a.id)
        assertEquals("A (copy)", copy.name)
        assertEquals("fov:0.5", Files.readString(repo.gameDirectory(copy).resolve("options.txt")))
    }

    @Test
    fun `siblings mirror the template and are reused`() {
        val template = repo.create("PvP", "26.3", LoaderType.FABRIC)
        Files.createDirectories(repo.gameDirectory(template).resolve("mods"))
        Files.writeString(repo.gameDirectory(template).resolve("options.txt"), "key_key.jump:key.keyboard.space")
        Files.writeString(repo.gameDirectory(template).resolve("mods/extra.jar"), "jar")

        val sibling = repo.siblingFor(template, 2)
        assertEquals("PvP #2", sibling.name)
        assertEquals(template.id, sibling.linkedTo)
        assertTrue(Files.exists(repo.gameDirectory(sibling).resolve("options.txt")))
        assertTrue(Files.exists(repo.gameDirectory(sibling).resolve("mods/extra.jar")))

        val updated = repo.save(template.copy(gameVersion = "1.21.11", maxMemoryMb = 2048))
        val again = repo.siblingFor(updated, 2)
        assertEquals(sibling.id, again.id)
        assertEquals("1.21.11", again.gameVersion)
        assertEquals(2048, again.maxMemoryMb)
    }

    @Test
    fun `legacy default folder is adopted`() {
        Files.createDirectories(paths.instancesDir.resolve("default/minecraft"))
        val instances = InstanceRepository(paths).load()
        assertEquals("Default", instances.single().name)
    }

    @Test
    fun `delete removes the instance folder`() {
        val a = repo.create("A", "26.3", LoaderType.FABRIC)
        repo.delete(a.id)
        assertFalse(Files.exists(repo.instanceDir(a.id)))
        assertTrue(repo.instances.value.isEmpty())
    }

    @Test
    fun `game folder lock is exclusive and respects a still-running game`() {
        val gameDir = dir.resolve("game")
        GameDirLock.acquire(gameDir).use {
            assertFailsWith<GameDirInUseException> { GameDirLock.acquire(gameDir) }
        }
        // A launcher that exited while its game kept running leaves the game's PID behind.
        Files.writeString(gameDir.resolve(GameDirLock.FILE_NAME), "123456")
        assertFailsWith<GameDirInUseException> { GameDirLock.acquire(gameDir, isAlive = { true }) }
        GameDirLock.acquire(gameDir, isAlive = { false }).close()
    }

    @Test
    fun `log buffer keeps the newest lines and serves increments`() {
        val buffer = LogBuffer(capacity = 3)
        repeat(5) { buffer.append(LogLine(LogSource.STDOUT, LogLevel.INFO, "line $it")) }
        assertEquals(listOf("line 2", "line 3", "line 4"), buffer.after(-1).map { it.line.message })
        assertEquals(listOf("line 4"), buffer.after(3).map { it.line.message })
        assertTrue(buffer.after(4).isEmpty())
        assertEquals(4, buffer.lastSeq.value)
    }

    @Test
    fun `JVM argument text splits like a shell`() {
        val args = ArgumentSplitter.split("""-Xss2M  "-Dfoo=a b" -XX:+UseG1GC """"")
        assertEquals(listOf("-Xss2M", "-Dfoo=a b", "-XX:+UseG1GC", ""), args)
        assertEquals(args, ArgumentSplitter.split(ArgumentSplitter.join(args)))
    }
}
