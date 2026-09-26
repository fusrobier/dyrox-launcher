package net.dyrox.client.module.modules.movement

import net.dyrox.client.event.PlayerPreTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.PlayerUtil
import net.dyrox.client.util.mc

/** Climb walls like a spider: walking into a wall moves you up. */
object Spider : Module("Spider", Category.MOVEMENT, "Climb up walls.") {
    private val speed by float("Speed", 0.2f, 0.05f..0.6f, 0.01f, "Climb speed in blocks per tick")

    @Suppress("unused")
    private val onTick = handler<PlayerPreTickEvent> {
        val player = mc.player ?: return@handler
        if (player.horizontalCollision && PlayerUtil.isMoving(player)) {
            val motion = player.deltaMovement
            player.setDeltaMovement(motion.x, speed.toDouble(), motion.z)
        }
    }
}
