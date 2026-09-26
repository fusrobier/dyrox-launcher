package net.dyrox.launcher.core.process

/**
 * Parses the game's console output. With Mojang's logging config the console uses log4j's
 * LegacyXMLLayout, so each record arrives as a multi-line `<log4j:Event>` block; anything else
 * (early JVM errors, System.out prints) is passed through as a plain line.
 *
 * Not thread-safe: use one instance per stream.
 */
class Log4jXmlParser(private val source: LogSource = LogSource.STDOUT) {
    private val buffer = StringBuilder()
    private var inEvent = false

    /** Feeds one line of output; returns the records it completed (usually zero or one). */
    fun feed(line: String): List<LogLine> {
        if (!inEvent) {
            if (!line.trimStart().startsWith(EVENT_START)) return listOf(plainLine(line))
            inEvent = true
            buffer.setLength(0)
        }
        buffer.append(line).append('\n')
        if (!line.contains(EVENT_END)) return emptyList()
        inEvent = false
        return listOfNotNull(parseEvent(buffer.toString()))
    }

    /** Emits whatever is buffered, e.g. when the process exits mid-record. */
    fun flush(): List<LogLine> {
        if (!inEvent) return emptyList()
        inEvent = false
        return listOf(plainLine(buffer.toString().trimEnd()))
    }

    private fun parseEvent(xml: String): LogLine? {
        val header = HEADER.find(xml) ?: return plainLine(xml.trimEnd())
        val attributes = ATTRIBUTE.findAll(header.groupValues[1]).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
        val message = sectionText(xml, "Message")
        val throwable = sectionText(xml, "Throwable")
        val text = if (throwable.isBlank()) message else "$message\n${throwable.trimEnd()}"
        return LogLine(
            source = source,
            level = LogLevel.parse(attributes["level"]) ?: LogLevel.INFO,
            message = text.trimEnd('\n', '\r'),
            thread = attributes["thread"],
            logger = attributes["logger"],
            timestamp = attributes["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
        )
    }

    /** Concatenates every CDATA section of `<log4j:name>`; log4j splits a literal `]]>` across two sections. */
    private fun sectionText(xml: String, name: String): String {
        val start = xml.indexOf("<log4j:$name>")
        if (start < 0) return ""
        val end = xml.indexOf("</log4j:$name>", start)
        if (end < 0) return ""
        val body = xml.substring(start + name.length + 8, end)
        val sections = CDATA.findAll(body).map { it.groupValues[1] }.toList()
        return if (sections.isEmpty()) unescape(body.trim()) else sections.joinToString("")
    }

    private fun plainLine(line: String): LogLine {
        val level = PLAIN_LEVEL.find(line)?.groupValues?.get(1)?.let(LogLevel::parse)
            ?: if (source == LogSource.STDERR) LogLevel.ERROR else LogLevel.INFO
        return LogLine(source = source, level = level, message = line)
    }

    private companion object {
        const val EVENT_START = "<log4j:Event"
        const val EVENT_END = "</log4j:Event>"
        val HEADER = Regex("""<log4j:Event\s+([^>]*)>""")
        val ATTRIBUTE = Regex("""(\w+)="([^"]*)"""")
        val CDATA = Regex("""<!\[CDATA\[(.*?)]]>""", RegexOption.DOT_MATCHES_ALL)
        /** `[12:34:56] [Render thread/WARN]: ...` as written by the default PatternLayout. */
        val PLAIN_LEVEL = Regex("""^\[[^\]]*] \[[^\]]*/(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)]""")

        fun unescape(value: String) = value
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}
