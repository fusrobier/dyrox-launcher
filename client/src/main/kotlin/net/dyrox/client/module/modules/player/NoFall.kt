package net.dyrox.client.module.modules.player

import net.dyrox.client.event.PreMotionEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc

/**
 * No fall damage. The server computes fall damage from the ground flag in your movement packets and
 * resets the fall distance whenever you report being on ground; claiming ground once you have fallen
 * 2 blocks (less than the 3-block safe distance) keeps that distance from ever reaching damage.
 */
object NoFall : Module("NoFall", Category.PLAYER, "Prevents fall damage.") {
    @Suppress("unused")
    private val onMotion = handler<PreMotionEvent> { event ->
        val player = mc.player ?: return@handler
        if (player.isCreative || player.isSpectator || player.isFallFlying) return@handler
        if (player.fallDistance > 2.0) event.groundOverride = true
    }
}
