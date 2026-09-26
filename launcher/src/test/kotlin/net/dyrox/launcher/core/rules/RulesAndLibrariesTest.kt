package net.dyrox.launcher.core.rules

import net.dyrox.launcher.core.Fixtures
import net.dyrox.launcher.core.library.LibraryResolver
import net.dyrox.launcher.core.library.MavenCoordinate
import net.dyrox.launcher.core.version.Library
import net.dyrox.launcher.core.version.OsCondition
import net.dyrox.launcher.core.version.Rule
import net.dyrox.shared.platform.Architecture
import net.dyrox.shared.platform.OperatingSystem
import net.dyrox.shared.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesAndLibrariesTest {
    private val allow = Rule(Rule.Action.ALLOW)
    private fun allowOs(name: String? = null, arch: String? = null, version: String? = null) =
        Rule(Rule.Action.ALLOW, OsCondition(name, version, arch))
    private fun disallowOs(name: String) = Rule(Rule.Action.DISALLOW, OsCondition(name))

    @Test
    fun `no rules means allowed`() {
        assertTrue(Rules.allows(emptyList(), RuleContext(Fixtures.windows)))
    }

    @Test
    fun `last matching rule wins`() {
        val allExceptMac = listOf(allow, disallowOs("osx"))
        assertTrue(Rules.allows(allExceptMac, RuleContext(Fixtures.windows)))
        assertFalse(Rules.allows(allExceptMac, RuleContext(Fixtures.macArm)))
    }

    @Test
    fun `os name, arch and version regex are all checked`() {
        assertTrue(Rules.allows(listOf(allowOs("windows")), RuleContext(Fixtures.windows)))
        assertFalse(Rules.allows(listOf(allowOs("windows")), RuleContext(Fixtures.linux)))
        // "-Xss1M" is only for 32-bit JVMs.
        assertFalse(Rules.allows(listOf(allowOs(arch = "x86")), RuleContext(Fixtures.windows)))
        assertTrue(Rules.allows(listOf(allowOs(arch = "x86")), RuleContext(Platform(OperatingSystem.WINDOWS, Architecture.X86, "10.0"))))
        val oldMac = Platform(OperatingSystem.MACOS, Architecture.X86_64, "10.5.8")
        assertTrue(Rules.allows(listOf(allowOs("osx", version = "^10\\.5\\.\\d$")), RuleContext(oldMac)))
        assertFalse(Rules.allows(listOf(allowOs("osx", version = "^10\\.5\\.\\d$")), RuleContext(Fixtures.macArm)))
    }

    @Test
    fun `feature rules require the feature to match`() {
        val rules = listOf(Rule(Rule.Action.ALLOW, features = mapOf(Features.CUSTOM_RESOLUTION to true)))
        assertFalse(Rules.allows(rules, RuleContext(Fixtures.windows)))
        assertTrue(Rules.allows(rules, RuleContext(Fixtures.windows, setOf(Features.CUSTOM_RESOLUTION))))
    }

    @Test
    fun `maven coordinates parse into repository paths`() {
        val plain = MavenCoordinate.parse("org.ow2.asm:asm:9.10.1")
        assertEquals("org/ow2/asm/asm/9.10.1/asm-9.10.1.jar", plain.path)
        assertEquals("org.ow2.asm:asm", plain.versionlessKey)

        val natives = MavenCoordinate.parse("org.lwjgl:lwjgl:3.4.3:natives-windows")
        assertEquals("org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar", natives.path)
        assertEquals("org.lwjgl:lwjgl:natives-windows", natives.versionlessKey)

        assertEquals("a/b/1/b-1.zip", MavenCoordinate.parse("a:b:1@zip").path)
        assertFailsWith<IllegalArgumentException> { MavenCoordinate.parse("broken") }
    }

    @Test
    fun `modern natives are plain classpath jars filtered by OS`() {
        val libraries = Fixtures.version(Fixtures.VANILLA_26_3).libraries
        val windows = LibraryResolver.resolve(libraries, RuleContext(Fixtures.windows)).map { it.coordinate.toString() }
        assertTrue("org.lwjgl:lwjgl:3.4.3:natives-windows" in windows)
        assertTrue(windows.none { it.contains("natives-linux") || it.contains("natives-macos") })

        val linux = LibraryResolver.resolve(libraries, RuleContext(Fixtures.linux)).map { it.coordinate.toString() }
        assertTrue("org.lwjgl:lwjgl:3.4.3:natives-linux" in linux)
        assertTrue(linux.none { it.contains("natives-windows") })
    }

    @Test
    fun `legacy natives resolve to classifier jars with extract excludes`() {
        val libraries = Fixtures.version(Fixtures.LEGACY_1_8_9).libraries
        val resolved = LibraryResolver.resolve(libraries, RuleContext(Fixtures.windows))
        val platform = resolved.single { it.coordinate.artifact == "lwjgl-platform" }
        val native = assertNotNull(platform.native)
        assertTrue(native.path.endsWith("lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar"))
        assertEquals(listOf("META-INF/"), platform.extractExcludes)
        assertNotNull(native.sha1)
    }

    @Test
    fun `arch placeholder in native classifiers becomes the JVM bitness`() {
        val library = Library(name = "tv.twitch:twitch-platform:5.16", natives = mapOf("windows" to "natives-windows-\${arch}"))
        val resolved = assertNotNull(LibraryResolver.resolve(library, RuleContext(Fixtures.windows)))
        assertNull(resolved.artifact)
        assertEquals("tv/twitch/twitch-platform/5.16/twitch-platform-5.16-natives-windows-64.jar", resolved.native?.path)
        assertEquals(LibraryResolver.MOJANG_LIBRARIES + resolved.native?.path, resolved.native?.url)
    }

    @Test
    fun `fabric style libraries use their maven url`() {
        val library = Fixtures.version(Fixtures.FABRIC_26_3).libraries.single { it.name == "net.fabricmc:fabric-loader:0.19.5" }
        val file = assertNotNull(LibraryResolver.resolve(library, RuleContext(Fixtures.windows))?.artifact)
        assertEquals("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar", file.url)
        assertNull(file.sha1)
    }
}
