package net.dyrox.launcher.core.java

import net.dyrox.launcher.core.Fixtures
import net.dyrox.shared.platform.Architecture
import net.dyrox.shared.platform.OperatingSystem
import net.dyrox.shared.platform.Platform
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JavaRuntimeManagerTest {
    @Test
    fun `maps platforms to Mojang runtime catalog keys`() {
        assertEquals("windows-x64", JavaRuntimeManager.platformKey(Fixtures.windows))
        assertEquals("linux", JavaRuntimeManager.platformKey(Fixtures.linux))
        assertEquals("mac-os-arm64", JavaRuntimeManager.platformKey(Fixtures.macArm))
        assertEquals("windows-arm64", JavaRuntimeManager.platformKey(Platform(OperatingSystem.WINDOWS, Architecture.ARM64, "")))
        // Mojang ships no Linux ARM runtime; the user must pick a custom Java.
        assertNull(JavaRuntimeManager.platformKey(Platform(OperatingSystem.LINUX, Architecture.ARM64, "")))
    }

    @Test
    fun `uses javaw on Windows so no console window opens`() {
        val dir = Path.of("runtime")
        assertEquals(dir.resolve("bin").resolve("javaw.exe"), JavaRuntimeManager.javaExecutable(dir, OperatingSystem.WINDOWS))
        assertEquals(dir.resolve("bin").resolve("java"), JavaRuntimeManager.javaExecutable(dir, OperatingSystem.LINUX))
    }
}
