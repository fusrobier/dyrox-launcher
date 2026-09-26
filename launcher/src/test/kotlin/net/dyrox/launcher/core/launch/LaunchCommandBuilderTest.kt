package net.dyrox.launcher.core.launch

import kotlinx.coroutines.runBlocking
import net.dyrox.launcher.core.Fixtures
import net.dyrox.launcher.core.LauncherPaths
import net.dyrox.launcher.core.install.InstalledGame
import net.dyrox.shared.platform.Platform
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Launch-command generation against real Mojang/Fabric JSONs. These are the arguments the game
 * actually receives, so each assertion here corresponds to a way a launch can break.
 */
class LaunchCommandBuilderTest {
    private val paths = LauncherPaths(Path.of("dyrox-test-root").toAbsolutePath())
    private val builder = LaunchCommandBuilder("dyrox-launcher", "1.2.3")
    private val token = "secret-access-token-1234567890"
    private val identity = LaunchIdentity(
        username = "Steve",
        uuid = "069a79f444e94726a5befca90e38aaf5",
        accessToken = token,
        userType = LaunchIdentity.USER_TYPE_MSA,
        xuid = "2535400000000000",
        clientId = "client-id",
    )
    private val gameDir = paths.instancesDir.resolve("test").resolve("minecraft")
    private val nativesDir = paths.instancesDir.resolve("test").resolve("natives")

    private fun build(id: String, platform: Platform, resolution: Resolution? = null): LaunchCommand {
        val version = runBlocking { Fixtures.resolve(id) }
        val game = InstalledGame.layout(version, paths, platform)
        val options = LaunchOptions(
            javaExecutable = Path.of("java"),
            gameDirectory = gameDir,
            nativesDirectory = nativesDir,
            minMemoryMb = 512,
            maxMemoryMb = 4096,
            systemProperties = mapOf("dyrox.instance" to "test"),
            resolution = resolution,
        )
        return builder.build(game, identity, options, platform)
    }

    private fun LaunchCommand.valueAfter(flag: String): String = arguments[arguments.indexOf(flag) + 1]
    private fun Path.abs() = toAbsolutePath().normalize().toString()

    @Test
    fun `fabric 26_3 on Windows has the right JVM section`() {
        val command = build(Fixtures.FABRIC_26_3, Fixtures.windows)
        val args = command.arguments
        val mainIndex = args.indexOf("net.fabricmc.loader.impl.launch.knot.KnotClient")
        assertTrue(mainIndex > 0, "main class missing")

        for (jvmArg in listOf(
            "-Xms512M",
            "-Xmx4096M",
            "-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump",
            "-Djava.library.path=${nativesDir.abs()}/java",
            "-Dorg.lwjgl.system.SharedLibraryExtractPath=${nativesDir.abs()}/lwjgl",
            "-Dminecraft.launcher.brand=dyrox-launcher",
            "-Dminecraft.launcher.version=1.2.3",
            "-Dlog4j.configurationFile=${paths.logConfig("client-1.21.2.xml").abs()}",
            "-Ddyrox.instance=test",
            "-DFabricMcEmu= net.minecraft.client.main.Main ",
        )) {
            val index = args.indexOf(jvmArg)
            assertTrue(index in 0 until mainIndex, "expected JVM argument before main class: $jvmArg")
        }
        assertFalse("-XstartOnFirstThread" in args, "macOS-only argument leaked")
        assertFalse("-Xss1M" in args, "32-bit-only argument leaked")
    }

    @Test
    fun `fabric 26_3 classpath has one ASM, OS natives only, and the client jar last`() {
        val command = build(Fixtures.FABRIC_26_3, Fixtures.windows)
        val classpath = command.valueAfter("-cp").split(";")
        assertEquals(paths.versionJar("26.3").abs(), classpath.last())
        assertEquals(classpath.size, classpath.toSet().size, "duplicate classpath entries")

        val asmJars = classpath.map { Path.of(it).fileName.toString() }.filter { Regex("""asm-\d.*\.jar""").matches(it) }
        assertEquals(listOf("asm-9.10.1.jar"), asmJars)
        assertTrue(classpath.any { it.endsWith("lwjgl-3.4.3-natives-windows.jar") })
        assertTrue(classpath.none { "natives-linux" in it || "natives-macos" in it })
        assertTrue(classpath.any { it.endsWith("fabric-loader-0.19.5.jar") })
    }

    @Test
    fun `fabric 26_3 game arguments are fully substituted`() {
        val command = build(Fixtures.FABRIC_26_3, Fixtures.windows)
        assertEquals("Steve", command.valueAfter("--username"))
        assertEquals(Fixtures.FABRIC_26_3, command.valueAfter("--version"))
        assertEquals(gameDir.abs(), command.valueAfter("--gameDir"))
        assertEquals(paths.assetsDir.abs(), command.valueAfter("--assetsDir"))
        assertEquals("34", command.valueAfter("--assetIndex"))
        assertEquals(identity.uuid, command.valueAfter("--uuid"))
        assertEquals(token, command.valueAfter("--accessToken"))
        assertEquals("client-id", command.valueAfter("--clientId"))
        assertEquals("2535400000000000", command.valueAfter("--xuid"))
        assertEquals("release", command.valueAfter("--versionType"))

        assertTrue(command.arguments.none { "\${" in it }, "unresolved placeholder: ${command.arguments.filter { "\${" in it }}")
        assertFalse("--demo" in command.arguments)
        assertFalse("--width" in command.arguments, "resolution arguments without a custom resolution")
        assertFalse(command.arguments.any { it.startsWith("--quickPlay") })
    }

    @Test
    fun `custom resolution enables the resolution arguments`() {
        val command = build(Fixtures.FABRIC_26_3, Fixtures.windows, Resolution(1280, 720))
        assertEquals("1280", command.valueAfter("--width"))
        assertEquals("720", command.valueAfter("--height"))
    }

    @Test
    fun `access token never appears in the redacted command`() {
        val command = build(Fixtures.FABRIC_26_3, Fixtures.windows)
        val redacted = command.redactedCommandLine()
        assertTrue(redacted.none { token in it })
        assertTrue("********" in redacted)
        assertFalse(token in command.toString())
    }

    @Test
    fun `linux uses colon separators and linux natives`() {
        val command = build(Fixtures.FABRIC_26_3, Fixtures.linux)
        val classpath = command.valueAfter("-cp").split(":")
        assertTrue(classpath.any { it.endsWith("lwjgl-3.4.3-natives-linux.jar") })
        assertTrue(classpath.none { "natives-windows" in it })
        assertTrue(command.arguments.none { it.startsWith("-XX:HeapDumpPath") })
    }

    @Test
    fun `macOS gets XstartOnFirstThread`() {
        val command = build(Fixtures.VANILLA_26_3, Fixtures.macArm)
        assertTrue("-XstartOnFirstThread" in command.arguments)
        assertMainClassBeforeGameArguments(command, "net.minecraft.client.main.Main")
    }

    /** The main class must come after every JVM argument and right before the first game argument. */
    private fun assertMainClassBeforeGameArguments(command: LaunchCommand, mainClass: String) {
        val args = command.arguments
        val mainIndex = args.indexOf(mainClass)
        assertTrue(mainIndex > args.indexOf("-cp") + 1, "main class must follow the classpath")
        assertEquals("--username", args[mainIndex + 1])
        assertTrue(args.subList(0, mainIndex).none { it.startsWith("--") && it != "--enable-native-access=ALL-UNNAMED" && it != "--add-exports" })
    }

    @Test
    fun `legacy 1_8_9 uses default JVM arguments and minecraftArguments`() {
        val version = runBlocking { Fixtures.resolve(Fixtures.LEGACY_1_8_9) }
        val game = InstalledGame.layout(version, paths, Fixtures.windows)
        assertTrue(game.nativeArchives.any { it.jar.fileName.toString().endsWith("natives-windows.jar") })

        val command = build(Fixtures.LEGACY_1_8_9, Fixtures.windows, Resolution(1024, 768))
        val args = command.arguments
        assertTrue("-Djava.library.path=${nativesDir.abs()}" in args)
        assertMainClassBeforeGameArguments(command, "net.minecraft.client.main.Main")
        assertEquals("{}", command.valueAfter("--userProperties"))
        assertEquals("msa", command.valueAfter("--userType"))
        assertEquals("1.8", command.valueAfter("--assetIndex"))
        assertEquals("1024", command.valueAfter("--width"))

        val classpath = command.valueAfter("-cp").split(";")
        assertEquals(paths.versionJar("1.8.9").abs(), classpath.last())
        assertTrue(classpath.none { "natives-" in it }, "legacy natives jars belong in the natives dir, not the classpath")
        assertTrue(args.none { "\${" in it })
    }

    @Test
    fun `legacy versions on macOS get XstartOnFirstThread from the launcher`() {
        val command = build(Fixtures.LEGACY_1_8_9, Fixtures.macArm)
        assertTrue("-XstartOnFirstThread" in command.arguments)
    }
}
