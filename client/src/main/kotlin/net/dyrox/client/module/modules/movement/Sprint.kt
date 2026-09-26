package net.dyrox.client.module.modules.movement

import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.minecraft.client.Minecraft

/**
 * Holds the sprint key for you. Vanilla still decides whether sprinting is possible (moving forward,
 * enough food, not sneaking), so this behaves like a player holding Ctrl.
 */
object Sprint : Module("Sprint", Category.MOVEMENT, "Keeps you sprinting whenever vanilla allows it.") {
    /** Whether we pressed the key, so disabling only undoes our own press. */
    private var pressedByUs = false

    @Suppress("unused")
    private val onTick = handler<PlayerTickEvent> {
        val key = Minecraft.getInstance().options.keySprint
        if (!key.isDown) {
            // In "toggle sprint" mode setDown(true) flips the state, so only call it when it's up.
            key.setDown(true)
            pressedByUs = true
        }
    }

    override fun onDisable() {
        val options = Minecraft.getInstance().options
        if (pressedByUs && options.keySprint.isDown) {
            // Toggle mode: another setDown(true) flips it off. Hold mode: just release it.
            options.keySprint.setDown(options.toggleSprint().get())
        }
        pressedByUs = false
    }
}
