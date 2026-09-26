package net.dyrox.launcher.core.process

import net.dyrox.shared.platform.OperatingSystem
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsArgumentsTest {
    @Test
    fun `arguments without quotes are unchanged`() {
        for (arg in listOf("-Xmx4G", "C:\\Program Files\\Java\\bin\\javaw.exe", "trailing\\", "a b c", "")) {
            assertEquals(arg, WindowsArguments.escape(arg))
        }
    }

    @Test
    fun `quotes and the backslashes before them are escaped`() {
        assertEquals("""-Dx={\"a\":1}""", WindowsArguments.escape("""-Dx={"a":1}"""))
        assertEquals("""a\\\"b""", WindowsArguments.escape("""a\"b"""))
        assertEquals("""\"quoted\"""", WindowsArguments.escape(""""quoted""""))
        assertEquals("""x\y\"z""", WindowsArguments.escape("""x\y"z"""))
    }

    /** The real check: a child JVM must receive exactly what we passed, on Windows. */
    @Test
    fun `a child process receives the original values`() {
        assumeTrue(OperatingSystem.current == OperatingSystem.WINDOWS)
        val values = listOf(
            """{"a b": "c"}""",
            """plain""",
            """with space""",
            """"fully quoted"""",
            """back\slash "and quote"""",
            """ends with backslash\""",
            """spaces and trailing backslashes \\""",
            """mix \" of \\" everything \\\"""",
        )
        val java = Path.of(ProcessHandle.current().info().command().get()).toString()
        val arguments = values.mapIndexed { i, v -> "-Ddyrox.test$i=$v" } + listOf("-XshowSettings:properties", "-version")
        val process = ProcessBuilder(listOf(java) + WindowsArguments.escapeAll(arguments)).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        values.forEachIndexed { i, expected ->
            val line = output.lines().firstOrNull { it.trim().startsWith("dyrox.test$i = ") }
                ?: error("dyrox.test$i missing in:\n$output")
            assertEquals(expected, line.trim().removePrefix("dyrox.test$i = "), "value $i")
        }
    }
}
