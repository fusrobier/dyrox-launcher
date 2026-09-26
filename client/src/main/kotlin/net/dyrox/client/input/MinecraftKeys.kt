package net.dyrox.client.input

import com.mojang.blaze3d.platform.InputConstants
import net.dyrox.client.command.CommandException
import net.dyrox.client.command.KeyResolver

/** Validates key input against the running game's key table (SDL scancodes in 26.x). */
object MinecraftKeys : KeyResolver {
    override fun resolve(input: String): String? {
        val name = KeyNames.toMinecraftName(input) ?: return null
        if (name.startsWith("key.mouse.")) throw CommandException("Only keyboard keys can be bound for now")
        val key = try {
            InputConstants.getKey(name)
        } catch (_: RuntimeException) {
            throw CommandException("Unknown key '$input'")
        }
        if (key == InputConstants.UNKNOWN) throw CommandException("Unknown key '$input'")
        return key.name
    }
}
