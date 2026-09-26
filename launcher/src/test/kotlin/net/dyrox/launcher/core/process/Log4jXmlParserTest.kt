package net.dyrox.launcher.core.process

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Log4jXmlParserTest {
    private fun parse(parser: Log4jXmlParser, output: String) = output.lines().flatMap(parser::feed) + parser.flush()

    @Test
    fun `parses LegacyXMLLayout events`() {
        val output = """
            <log4j:Event logger="net.minecraft.client.Minecraft" timestamp="1760000000000" level="INFO" thread="Render thread">
              <log4j:Message><![CDATA[Setting user: Steve]]></log4j:Message>
            </log4j:Event>
        """.trimIndent()
        val line = parse(Log4jXmlParser(), output).single()
        assertEquals(LogLevel.INFO, line.level)
        assertEquals("Setting user: Steve", line.message)
        assertEquals("Render thread", line.thread)
        assertEquals("net.minecraft.client.Minecraft", line.logger)
        assertEquals(1760000000000, line.timestamp)
    }

    @Test
    fun `keeps multi-line messages and appends throwables`() {
        val output = """
            <log4j:Event logger="a" timestamp="1" level="ERROR" thread="main">
              <log4j:Message><![CDATA[first line
            second line]]></log4j:Message>
              <log4j:Throwable><![CDATA[java.lang.IllegalStateException: boom
            	at a.B.c(B.java:1)
            ]]></log4j:Throwable>
            </log4j:Event>
        """.trimIndent()
        val line = parse(Log4jXmlParser(), output).single()
        assertEquals(LogLevel.ERROR, line.level)
        assertTrue(line.message.startsWith("first line\nsecond line\njava.lang.IllegalStateException: boom"))
        assertTrue(line.message.contains("at a.B.c(B.java:1)"))
    }

    @Test
    fun `joins split CDATA sections and unescapes attributes`() {
        val output = """
            <log4j:Event logger="x" timestamp="1" level="WARN" thread="&lt;worker&gt; &amp; co">
              <log4j:Message><![CDATA[a]]]]><![CDATA[>b]]></log4j:Message>
            </log4j:Event>
        """.trimIndent()
        val line = parse(Log4jXmlParser(), output).single()
        assertEquals("a]]>b", line.message)
        assertEquals("<worker> & co", line.thread)
        assertEquals(LogLevel.WARN, line.level)
    }

    @Test
    fun `passes plain lines through with a best-effort level`() {
        val lines = parse(
            Log4jXmlParser(),
            "[12:00:01] [main/WARN]: Something odd\nError: Could not find or load main class foo",
        )
        assertEquals(listOf(LogLevel.WARN, LogLevel.INFO), lines.map { it.level })
        assertEquals("Error: Could not find or load main class foo", lines[1].message)
        assertEquals(LogLevel.ERROR, Log4jXmlParser(LogSource.STDERR).feed("Exception in thread main").single().level)
    }

    @Test
    fun `interleaved plain lines and events keep their order`() {
        val output = """
            plain before
            <log4j:Event logger="x" timestamp="1" level="INFO" thread="t">
              <log4j:Message><![CDATA[event]]></log4j:Message>
            </log4j:Event>
            plain after
        """.trimIndent()
        assertEquals(listOf("plain before", "event", "plain after"), parse(Log4jXmlParser(), output).map { it.message })
    }
}
