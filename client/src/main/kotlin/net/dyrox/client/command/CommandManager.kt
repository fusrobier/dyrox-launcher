package net.dyrox.client.command

/** Where command feedback goes: chat in-game, a list in tests. */
interface CommandOutput {
    fun info(message: String)
    fun error(message: String)
}

/** Thrown by commands for user mistakes; the message is shown as an error. */
class CommandException(message: String) : Exception(message)

abstract class Command(
    val name: String,
    val description: String,
    /** Arguments only, e.g. `<module> [key|none]`. */
    val usage: String = "",
    val aliases: List<String> = emptyList(),
) {
    abstract fun execute(args: List<String>, output: CommandOutput)

    /** Suggestions for the argument being typed ([args] ends with the partial one). */
    open fun complete(args: List<String>): List<String> = emptyList()

    protected fun fail(message: String): Nothing = throw CommandException(message)

    protected fun usageError(): Nothing = fail("Usage: $name $usage".trimEnd())
}

/**
 * Chat commands behind a configurable prefix (default `.`). Arguments are split on spaces;
 * double quotes group words (`.config save "my pvp"` isn't allowed as a name, but texts may use it).
 */
class CommandManager(prefix: String = ".") {
    private val registered = ArrayList<Command>()

    var prefix: String = prefix
        set(value) {
            require(value.isNotBlank() && value.length <= 3 && value.none(Char::isWhitespace) && !value.startsWith("/")) {
                "Prefix must be 1–3 characters, no spaces, and not start with /"
            }
            field = value
        }

    val commands: List<Command> get() = registered

    fun register(vararg commands: Command) {
        for (command in commands) {
            require((listOf(command.name) + command.aliases).none { find(it) != null }) { "Duplicate command ${command.name}" }
            registered += command
        }
    }

    fun find(name: String): Command? = registered.firstOrNull { c -> c.name.equals(name, true) || c.aliases.any { it.equals(name, true) } }

    fun isCommand(message: String): Boolean = message.startsWith(prefix) && message.length > prefix.length && !message[prefix.length].isWhitespace()

    /** Runs [message] if it's a command; returns false if it isn't one (so it should be sent as chat). */
    fun execute(message: String, output: CommandOutput): Boolean {
        if (!isCommand(message)) return false
        val tokens = tokenize(message.substring(prefix.length))
        val command = find(tokens.first())
        if (command == null) {
            output.error("Unknown command '${tokens.first()}'. Try ${prefix}help")
            return true
        }
        try {
            command.execute(tokens.drop(1), output)
        } catch (e: CommandException) {
            output.error(e.message ?: "Invalid command")
        } catch (e: Exception) {
            output.error("${command.name} failed: ${e.message ?: e.javaClass.simpleName}")
        }
        return true
    }

    /** Completions for a partially typed command line (without the prefix check). */
    fun complete(message: String): List<String> {
        if (!message.startsWith(prefix)) return emptyList()
        val body = message.substring(prefix.length)
        val tokens = tokenize(body).toMutableList()
        if (body.endsWith(" ")) tokens += ""
        if (tokens.size <= 1) {
            val partial = tokens.firstOrNull().orEmpty()
            return registered.map { it.name }.filter { it.startsWith(partial, true) }.sorted()
        }
        val command = find(tokens.first()) ?: return emptyList()
        val args = tokens.drop(1)
        return command.complete(args).filter { it.startsWith(args.last(), true) }
    }

    companion object {
        fun tokenize(input: String): List<String> {
            val tokens = ArrayList<String>()
            val current = StringBuilder()
            var quoted = false
            for (c in input) {
                when {
                    c == '"' -> quoted = !quoted
                    c.isWhitespace() && !quoted -> {
                        if (current.isNotEmpty()) tokens += current.toString()
                        current.setLength(0)
                    }
                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) tokens += current.toString()
            return tokens
        }
    }
}
