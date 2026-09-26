package net.dyrox.client.module.modules.movement

import net.dyrox.client.event.PlayerPreTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc

/** Holds the forward key for you. */
object AutoWalk : Module("AutoWalk", Category.MOVEMENT, "Walks forward automatically.") {
    @Suppress("unused")
    private val onTick = handler<PlayerPreTickEvent> {
        if (mc.gui.screen() == null) mc.options.keyUp.setDown(true)
    }

    override fun onDisable() {
        mc.options.keyUp.setDown(false)
    }
}
