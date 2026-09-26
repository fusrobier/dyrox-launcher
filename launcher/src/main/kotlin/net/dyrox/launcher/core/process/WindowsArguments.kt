package net.dyrox.launcher.core.process

/**
 * Windows passes a process one command-line string, which the child's C runtime splits back into
 * arguments (`CommandLineToArgvW` rules). `ProcessBuilder` wraps arguments containing spaces in quotes
 * but does not escape quotes *inside* an argument, so `-Dx={"a b":1}` reaches the game as `-Dx={a b:1}`.
 *
 * [escape] fixes that: every `"` becomes `\"`, and backslashes right before it are doubled (a
 * backslash only escapes something when it precedes a quote). Arguments without quotes are returned
 * unchanged, so paths and ordinary JVM options are untouched.
 */
object WindowsArguments {
    fun escape(argument: String): String {
        if ('"' !in argument) return argument
        val out = StringBuilder(argument.length + 8)
        var backslashes = 0
        for (c in argument) {
            when (c) {
                '\\' -> backslashes++
                '"' -> {
                    repeat(backslashes * 2 + 1) { out.append('\\') }
                    out.append('"')
                    backslashes = 0
                }
                else -> {
                    repeat(backslashes) { out.append('\\') }
                    backslashes = 0
                    out.append(c)
                }
            }
        }
        // Trailing backslashes: ProcessBuilder already handles those when it adds the closing quote.
        repeat(backslashes) { out.append('\\') }
        return out.toString()
    }

    fun escapeAll(arguments: List<String>): List<String> = arguments.map(::escape)
}
