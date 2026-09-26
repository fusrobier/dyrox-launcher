package net.dyrox.client.module.modules.player

import net.dyrox.client.event.GameTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc
import net.minecraft.client.gui.screens.DeathScreen

/** Respawns right away when you die (like pressing "Respawn" on the death screen). */
object AutoRespawn : Module("AutoRespawn", Category.PLAYER, "Respawns automatically after death.") {
    private val delay by int("Delay", 5, 0..100, description = "Ticks to wait on the death screen")

    private var ticksDead = 0

    @Suppress("unused")
    private val onTick = handler<GameTickEvent> {
        val player = mc.player
        if (player == null || mc.gui.screen() !is DeathScreen) {
            ticksDead = 0
            return@handler
        }
        if (ticksDead++ == delay) {
            // The death screen closes itself when the server confirms the respawn.
            player.respawn()
        }
    }
}
