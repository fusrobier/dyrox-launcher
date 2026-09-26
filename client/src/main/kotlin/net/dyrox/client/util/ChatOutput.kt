package net.dyrox.client.util

import net.dyrox.client.command.CommandOutput
import net.dyrox.shared.theme.DyroxPalette
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/** Client-side chat lines with a lime "Dyrox" tag. Only the local player sees them. */
object ChatOutput : CommandOutput {
    override fun info(message: String) = print(message, DyroxPalette.TEXT_PRIMARY)

    override fun error(message: String) = print(message, DyroxPalette.DANGER)

    private fun print(message: String, color: Int) {
        val minecraft = Minecraft.getInstance()
        minecraft.execute {
            val line = Component.literal("[Dyrox] ").withColor(DyroxPalette.ACCENT and 0xFFFFFF)
                .append(Component.literal(message).withColor(color and 0xFFFFFF))
            minecraft.gui.hud.chat.addClientSystemMessage(line)
        }
    }
}
