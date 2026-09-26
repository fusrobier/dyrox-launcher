package net.dyrox.client.module.modules.movement

import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.PlayerPreTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.PlayerUtil
import net.dyrox.client.util.mc
import kotlin.math.hypot

/**
 * - Strafe Hop: jumps automatically and keeps your speed in the key direction (turn in the air).
 * - Ground: faster walking without jumping.
 */
object Speed : Module("Speed", Category.MOVEMENT, "Moves you faster.") {
    enum class Mode(override val choiceName: String) : NamedChoice {
        STRAFE_HOP("Strafe Hop"),
        GROUND("Ground"),
    }

    private val mode by choice("Mode", Mode.STRAFE_HOP)
    private val speed by float("Speed", 0.36f, 0.2f..1.5f, 0.01f, "Blocks per tick (sprinting is ~0.28)")

    override val tag: String get() = mode.choiceName

    @Suppress("unused")
    private val onTick = handler<PlayerPreTickEvent> {
        val player = mc.player ?: return@handler
        if (!PlayerUtil.isMoving(player) || player.isInWater || player.isInLava || player.abilities.flying || player.isPassenger || player.isFallFlying) return@handler
        when (mode) {
            Mode.STRAFE_HOP -> {
                if (player.onGround()) {
                    player.jumpFromGround()
                    PlayerUtil.strafe(player, speed.toDouble())
                } else {
                    // Keep current speed but steer it towards the keys.
                    val motion = player.deltaMovement
                    PlayerUtil.strafe(player, maxOf(hypot(motion.x, motion.z), speed * 0.8))
                }
            }
            Mode.GROUND -> if (player.onGround()) PlayerUtil.strafe(player, speed.toDouble())
        }
    }
}
