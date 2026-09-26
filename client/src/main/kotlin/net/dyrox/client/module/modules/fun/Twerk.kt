package net.dyrox.client.module.modules.`fun`

import net.dyrox.client.event.PlayerPreTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc

/** Sneaks on and off rapidly. */
object Twerk : Module("Twerk", Category.FUN, "Sneak on and off rapidly.") {
    private val interval by int("Interval", 2, 1..10, description = "Ticks per sneak toggle")

    private var ticks = 0
    private var sneaking = false

    @Suppress("unused")
    private val onTick = handler<PlayerPreTickEvent> {
        if (mc.gui.screen() != null || ++ticks < interval) return@handler
        ticks = 0
        setSneak(!sneaking)
    }

    override fun onDisable() {
        if (sneaking) setSneak(false)
    }

    private fun setSneak(on: Boolean) {
        sneaking = on
        val key = mc.options.keyShift
        // In "toggle sneak" mode every setDown(true) flips the state.
        if (mc.options.toggleCrouch().get()) {
            if (key.isDown != on) key.setDown(true)
        } else {
            key.setDown(on)
        }
    }
}
