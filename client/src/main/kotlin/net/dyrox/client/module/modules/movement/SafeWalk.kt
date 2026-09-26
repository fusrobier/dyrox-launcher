package net.dyrox.client.module.modules.movement

import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc

/** Stops you at block edges as if sneaking, without the sneak slowdown. */
object SafeWalk : Module("SafeWalk", Category.MOVEMENT, "Never walk off edges.") {
    private val onlyOnGround by boolean("Only on ground", true, "Allow jumping over gaps")

    fun shouldHoldEdge(): Boolean {
        if (!enabled) return false
        val player = mc.player ?: return false
        return !onlyOnGround || player.onGround()
    }
}
