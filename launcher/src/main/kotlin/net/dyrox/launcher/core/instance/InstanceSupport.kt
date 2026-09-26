package net.dyrox.launcher.core.instance

import java.nio.file.Files
import java.nio.file.Path

object CrashReports {
    /** The newest crash report (or JVM `hs_err` log) written at or after [since], if any. */
    fun find(gameDir: Path, since: Long): Path? {
        val candidates = buildList {
            val reports = gameDir.resolve("crash-reports")
            if (Files.isDirectory(reports)) Files.list(reports).use { s -> addAll(s.filter { it.fileName.toString().endsWith(".txt") }.toList()) }
            if (Files.isDirectory(gameDir)) Files.list(gameDir).use { s -> addAll(s.filter { it.fileName.toString().startsWith("hs_err_pid") }.toList()) }
        }
        return candidates
            .filter { Files.getLastModifiedTime(it).toMillis() >= since - 1_000 }
            .maxByOrNull { Files.getLastModifiedTime(it).toMillis() }
    }
}

/** Splits a JVM-arguments text field the way a shell would: spaces separate, double quotes group. */
object ArgumentSplitter {
    fun split(text: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var hasToken = false
        for (c in text) {
            when {
                c == '"' -> {
                    quoted = !quoted
                    hasToken = true
                }
                c.isWhitespace() && !quoted -> {
                    if (hasToken) result += current.toString()
                    current.setLength(0)
                    hasToken = false
                }
                else -> {
                    current.append(c)
                    hasToken = true
                }
            }
        }
        if (hasToken) result += current.toString()
        return result
    }

    fun join(arguments: List<String>): String =
        arguments.joinToString(" ") { if (it.isEmpty() || it.any(Char::isWhitespace)) "\"$it\"" else it }
}
