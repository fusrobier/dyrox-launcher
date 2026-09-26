package net.dyrox.client.command

import net.dyrox.client.config.BindMode
import net.dyrox.client.config.ConfigSystem
import net.dyrox.client.config.KeyBind
import net.dyrox.client.input.KeyNames
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.module.ModuleManager
import net.dyrox.client.module.TriggerModule

/** Resolves user key input to a valid Minecraft key name; throws [CommandException] if unknown. */
fun interface KeyResolver {
    fun resolve(input: String): String?
}

class HelpCommand(private val manager: CommandManager) : Command("help", "Lists commands or explains one", "[command]", listOf("?")) {
    override fun execute(args: List<String>, output: CommandOutput) {
        val name = args.firstOrNull()
        if (name != null) {
            val command = manager.find(name) ?: fail("No command '$name'")
            output.info("${manager.prefix}${command.name} ${command.usage}".trimEnd() + " — ${command.description}")
            if (command.aliases.isNotEmpty()) output.info("Aliases: " + command.aliases.joinToString { manager.prefix + it })
            return
        }
        manager.commands.forEach { output.info("${manager.prefix}${it.name} ${it.usage}".trimEnd() + " — ${it.description}") }
    }

    override fun complete(args: List<String>) = if (args.size == 1) manager.commands.map { it.name } else emptyList()
}

class ToggleCommand(private val modules: ModuleManager) : Command("toggle", "Turns a module on or off", "<module> [on|off]", listOf("t")) {
    override fun execute(args: List<String>, output: CommandOutput) {
        val module = modules[args.firstOrNull() ?: usageError()] ?: fail("No module '${args[0]}'")
        if (module is TriggerModule) {
            module.trigger()
            return
        }
        when (args.getOrNull(1)?.lowercase()) {
            null -> module.toggle()
            "on", "true", "enable" -> module.enabled = true
            "off", "false", "disable" -> module.enabled = false
            else -> usageError()
        }
        output.info("${module.name} ${if (module.enabled) "§aenabled" else "§cdisabled"}")
    }

    override fun complete(args: List<String>) = when (args.size) {
        1 -> modules.modules.map { it.name.replace(" ", "") }
        2 -> listOf("on", "off")
        else -> emptyList()
    }
}

class BindCommand(private val modules: ModuleManager, private val keys: KeyResolver) :
    Command("bind", "Binds a module to a key (hold = active only while held)", "<module> <key|none> [toggle|hold]", listOf("b")) {
    override fun execute(args: List<String>, output: CommandOutput) {
        if (args.size < 2) usageError()
        val module = modules[args[0]] ?: fail("No module '${args[0]}'")
        val key = keys.resolve(args[1])
        val mode = when (args.getOrNull(2)?.lowercase()) {
            null -> module.bind.value.mode
            "toggle" -> BindMode.TOGGLE
            "hold" -> BindMode.HOLD
            else -> usageError()
        }
        module.bind.value = KeyBind(key, mode)
        if (key == null) {
            output.info("Unbound ${module.name}")
        } else {
            output.info("Bound ${module.name} to ${KeyNames.label(key)}" + if (mode == BindMode.HOLD) " (hold)" else "")
        }
    }

    override fun complete(args: List<String>) = when (args.size) {
        1 -> modules.modules.map { it.name.replace(" ", "") }
        2 -> listOf("none", "rshift", "r", "g", "v", "x", "z", "f", "c")
        3 -> listOf("toggle", "hold")
        else -> emptyList()
    }
}

class BindsCommand(private val modules: ModuleManager) : Command("binds", "Lists all keybinds") {
    override fun execute(args: List<String>, output: CommandOutput) {
        val bound = modules.modules.filter { it.bind.value.isBound }
        if (bound.isEmpty()) output.info("No keybinds yet. Use bind <module> <key>")
        bound.forEach { output.info("${it.name}: ${KeyNames.label(it.bind.value.key)}" + if (it.bind.value.mode == BindMode.HOLD) " (hold)" else "") }
    }
}

class ModulesCommand(private val modules: ModuleManager) : Command("modules", "Lists modules, optionally of one category", "[category]", listOf("list")) {
    override fun execute(args: List<String>, output: CommandOutput) {
        val category = args.firstOrNull()?.let { name -> Category.entries.firstOrNull { it.name.equals(name, true) } ?: fail("No category '$name'") }
        val shown = if (category == null) modules.modules else modules.byCategory(category)
        shown.groupBy(Module::category).forEach { (cat, list) ->
            output.info("${cat.displayName}: " + list.joinToString { (if (it.enabled) "§a" else "§7") + it.name + "§r" })
        }
    }

    override fun complete(args: List<String>) = if (args.size == 1) Category.entries.map { it.name.lowercase() } else emptyList()
}

class ConfigCommand(private val config: ConfigSystem) :
    Command("config", "Manages config profiles", "<load|save|list|delete> [name]", listOf("profile", "cfg")) {
    override fun execute(args: List<String>, output: CommandOutput) {
        val action = args.firstOrNull()?.lowercase() ?: usageError()
        val name = args.getOrNull(1)
        if (name != null && !ConfigSystem.isValidName(name)) fail("Profile names use letters, digits, - and _ (max 32)")
        when (action) {
            "list" -> {
                val names = (config.profileNames() + config.activeProfile).distinct().sorted()
                output.info("Profiles: " + names.joinToString { if (it == config.activeProfile) "§a$it§r (active)" else it })
            }
            "save" -> {
                if (name == null || name == config.activeProfile) {
                    config.save()
                    output.info("Saved profile ${config.activeProfile}")
                } else {
                    config.saveAs(name)
                    output.info("Saved as $name (now active)")
                }
            }
            "load" -> {
                name ?: usageError()
                if (!config.exists(name)) fail("No profile '$name'. Use config list")
                config.save() // don't lose unsaved changes to the current profile
                val problems = config.load(name)
                output.info("Loaded profile $name")
                problems.take(5).forEach(output::error)
            }
            "delete" -> {
                name ?: usageError()
                if (name == config.activeProfile) fail("Can't delete the active profile")
                if (config.delete(name)) output.info("Deleted $name") else fail("No profile '$name'")
            }
            else -> usageError()
        }
    }

    override fun complete(args: List<String>) = when (args.size) {
        1 -> listOf("load", "save", "list", "delete")
        2 -> config.profileNames()
        else -> emptyList()
    }
}

class PrefixCommand(private val manager: CommandManager, private val config: ConfigSystem) : Command("prefix", "Changes the command prefix", "<prefix>") {
    override fun execute(args: List<String>, output: CommandOutput) {
        val prefix = args.singleOrNull() ?: usageError()
        try {
            manager.prefix = prefix
        } catch (e: IllegalArgumentException) {
            fail(e.message ?: "Invalid prefix")
        }
        config.updateState { it.copy(commandPrefix = prefix) }
        output.info("Command prefix is now $prefix")
    }
}
