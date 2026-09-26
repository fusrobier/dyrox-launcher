package net.dyrox.launcher.core.version

import kotlinx.coroutines.test.runTest
import net.dyrox.launcher.core.Fixtures
import net.dyrox.shared.json.DyroxJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionParsingAndResolutionTest {
    @Test
    fun `parses both argument shapes of the 26_3 JSON`() {
        val json = Fixtures.version(Fixtures.VANILLA_26_3)
        val args = assertNotNull(json.arguments)
        val username = args.game.first()
        assertEquals(listOf("--username"), username.values)
        assertTrue(username.rules.isEmpty())

        val resolution = args.game.single { "--width" in it.values }
        assertEquals(listOf("--width", "\${resolution_width}", "--height", "\${resolution_height}"), resolution.values)
        assertEquals(mapOf("has_custom_resolution" to true), resolution.rules.single().features)

        val macOnly = args.jvm.first()
        assertEquals(listOf("-XstartOnFirstThread"), macOnly.values)
        assertEquals("osx", macOnly.rules.single().os?.name)
        assertEquals(25, json.javaVersion?.majorVersion)
    }

    @Test
    fun `argument serializer round-trips`() {
        val original = Fixtures.version(Fixtures.VANILLA_26_3)
        val encoded = DyroxJson.encodeToString(VersionJson.serializer(), original)
        assertEquals(original, DyroxJson.decodeFromString(VersionJson.serializer(), encoded))
    }

    @Test
    fun `parses legacy 1_8_9 JSON`() {
        val json = Fixtures.version(Fixtures.LEGACY_1_8_9)
        assertNull(json.arguments)
        assertTrue(json.minecraftArguments!!.startsWith("--username \${auth_player_name}"))
        assertTrue(json.libraries.any { it.natives != null && it.extract != null })
    }

    @Test
    fun `merges the Fabric profile over vanilla`() = runTest {
        val resolved = Fixtures.resolve(Fixtures.FABRIC_26_3)

        assertEquals(Fixtures.FABRIC_26_3, resolved.id)
        assertEquals("26.3", resolved.gameVersion)
        assertEquals("26.3", resolved.jarId)
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", resolved.mainClass)
        assertEquals("34", resolved.assetIndex.id)
        assertEquals("java-runtime-epsilon", resolved.javaVersion?.component)
        assertNotNull(resolved.clientDownload)
        assertNotNull(resolved.logging)

        // Fabric's libraries come first, vanilla's follow.
        assertEquals("org.ow2.asm:asm:9.10.1", resolved.libraries.first().name)
        assertTrue(resolved.libraries.any { it.name.startsWith("org.lwjgl:lwjgl:") })
        // Vanilla JVM arguments first, Fabric's addition last.
        assertEquals("-DFabricMcEmu= net.minecraft.client.main.Main ", resolved.jvmArguments.last().values.single())
        assertEquals("\${classpath}", resolved.jvmArguments[resolved.jvmArguments.size - 2].values.single())
    }

    @Test
    fun `child libraries override parent libraries with the same key`() = runTest {
        val parent = VersionJson(
            id = "parent", mainClass = "Main", assetIndex = AssetIndexRef("1", "0".repeat(40), url = "u"),
            libraries = listOf(Library("org.ow2.asm:asm:9.0"), Library("com.example:keep:1.0")),
        )
        val child = VersionJson(id = "child", inheritsFrom = "parent", libraries = listOf(Library("org.ow2.asm:asm:9.10.1")))
        val resolved = VersionResolver { if (it == "child") child else parent }.resolve("child")
        assertEquals(listOf("org.ow2.asm:asm:9.10.1", "com.example:keep:1.0"), resolved.libraries.map { it.name })
    }

    @Test
    fun `detects cyclic inheritance`() = runTest {
        val a = VersionJson(id = "a", inheritsFrom = "b")
        val b = VersionJson(id = "b", inheritsFrom = "a")
        assertFailsWith<VersionResolutionException> {
            VersionResolver { if (it == "a") a else b }.resolve("a")
        }
    }
}
