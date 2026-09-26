package net.dyrox.client.module.modules.player

import net.dyrox.client.event.GameTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.mixin.MinecraftAccessor
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc

/** Shorter delay between right-click uses when holding the button (vanilla: 4 ticks). */
object FastPlace : Module("FastPlace", Category.PLAYER, "Place blocks and use items faster.") {
    private val delay by int("Delay", 0, 0..3, description = "Ticks between uses")

    @Suppress("unused")
    private val onTick = handler<GameTickEvent> {
        val accessor = mc as MinecraftAccessor
        if (accessor.`dyrox$getRightClickDelay`() > delay) accessor.`dyrox$setRightClickDelay`(delay)
    }
}
