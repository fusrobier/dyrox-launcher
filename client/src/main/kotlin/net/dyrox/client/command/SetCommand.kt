package net.dyrox.client.command

import net.dyrox.client.config.BooleanValue
import net.dyrox.client.config.ChoiceValue
import net.dyrox.client.config.ColorValue
import net.dyrox.client.config.DyroxColor
import net.dyrox.client.config.FloatRangeValue
import net.dyrox.client.config.FloatValue
import net.dyrox.client.config.IntRangeValue
import net.dyrox.client.config.IntValue
import net.dyrox.client.config.KeyValue
import net.dyrox.client.config.ModeValue
import net.dyrox.client.config.MultiChoiceValue
import net.dyrox.client.config.NamedChoice
import net.dyrox.client.config.TextValue
import net.dyrox.client.config.Value
import net.dyrox.client.module.Module
import net.dyrox.client.module.ModuleManager

/**
 * `.set <module> <setting> [value]`: shows or changes a setting from chat. Names ignore case and
 * spaces (`.set killaura turnspeed 40-80`). Multi-choice settings toggle the given entry.
 */
class SetCommand(private val modules: ModuleManager) :
    Command("set", "Shows or changes a module setting", "<module> <setting> [value]", listOf("value", "v")) {

    override fun execute(args: List<String>, output: CommandOutput) {
        if (args.size < 2) usageError()
        val module = modules[args[0]] ?: fail("No module '${args[0]}'")
        val value = find(module, args[1]) ?: fail("${module.name} has no setting '${args[1]}'. Settings: " + settingNames(module).joinToString())
        val input = args.drop(2).joinToString(" ")
        if (input.isEmpty()) {
            output.info("${module.name} › ${value.name} = ${display(value)}")
            return
        }
        apply(value, input)
        output.info("${module.name} › ${value.name} = ${display(value)}")
    }

    override fun complete(args: List<String>): List<String> = when (args.size) {
        1 -> modules.modules.map { it.name }.filter { it.startsWith(args[0], true) }
        2 -> modules[args[0]]?.let { m -> settingNames(m).map { it.replace(" ", "") }.filter { it.startsWith(args[1], true) } } ?: emptyList()
        else -> emptyList()
    }

    private fun settingNames(module: Module) = module.allValues().filter { it.name != Module.ENABLED && it.name != Module.BIND }.map { it.name }

    private fun find(module: Module, name: String): Value<*>? {
        val wanted = normalize(name)
        return module.allValues().firstOrNull { normalize(it.name) == wanted && it.name != Module.ENABLED }
    }

    @Suppress("UNCHECKED_CAST")
    private fun apply(value: Value<*>, input: String) {
        when (value) {
            is BooleanValue -> value.value = when (input.lowercase()) {
                "on", "true", "yes", "1" -> true
                "off", "false", "no", "0" -> false
                "toggle" -> !value.value
                else -> fail("Expected on/off")
            }
            is IntValue -> value.value = input.toIntOrNull() ?: fail("Expected a whole number in ${value.range}")
            is FloatValue -> value.value = input.toFloatOrNull() ?: fail("Expected a number in ${value.range}")
            is IntRangeValue -> {
                val (a, b) = range(input)
                value.value = a.toInt()..b.toInt()
            }
            is FloatRangeValue -> {
                val (a, b) = range(input)
                value.value = a.toFloat()..b.toFloat()
            }
            is ChoiceValue<*> -> {
                val choice = value.choices.firstOrNull { normalize(it.choiceName) == normalize(input) }
                    ?: fail("Choose one of: " + value.choices.joinToString { it.choiceName })
                (value as ChoiceValue<NamedChoice>).value = choice
            }
            is MultiChoiceValue<*> -> {
                val choice = value.choices.firstOrNull { normalize(it.choiceName) == normalize(input) }
                    ?: fail("Toggle one of: " + value.choices.joinToString { it.choiceName })
                (value as MultiChoiceValue<NamedChoice>).toggle(choice)
            }
            is ModeValue<*> -> if (!value.select(input)) fail("Choose one of: " + value.modes.joinToString { it.name })
            is ColorValue -> value.value = DyroxColor(
                runCatching { DyroxColor.parseHex(input) }.getOrElse { fail("Expected a colour like #FFB6FF3B") },
                value.value.rainbow,
            )
            is TextValue -> value.value = input
            is KeyValue -> fail("Use .bind to change keybinds")
            else -> fail("This setting can only be changed in the ClickGUI")
        }
    }

    private fun range(input: String): Pair<Double, Double> {
        val parts = input.split('-', ',', ' ').filter { it.isNotBlank() }
        val a = parts.getOrNull(0)?.toDoubleOrNull() ?: fail("Expected a range like 8-12")
        val b = parts.getOrNull(1)?.toDoubleOrNull() ?: a
        return minOf(a, b) to maxOf(a, b)
    }

    private fun display(value: Value<*>): String = when (value) {
        is MultiChoiceValue<*> -> value.value.joinToString { it.choiceName }.ifEmpty { "none" }
        is ChoiceValue<*> -> value.value.choiceName
        is ModeValue<*> -> value.value.name
        is ColorValue -> value.value.toHex()
        else -> value.value.toString()
    }

    private fun normalize(name: String) = name.replace(" ", "").lowercase()
}
