package net.dyrox.launcher.core.process

enum class LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

    companion object {
        fun parse(value: String?): LogLevel? = value?.let { v -> entries.firstOrNull { it.name.equals(v.trim(), ignoreCase = true) } }
    }
}

enum class LogSource { STDOUT, STDERR, LAUNCHER }

data class LogLine(
    val source: LogSource,
    val level: LogLevel,
    val message: String,
    val thread: String? = null,
    val logger: String? = null,
    /** Epoch millis. */
    val timestamp: Long = System.currentTimeMillis(),
)
