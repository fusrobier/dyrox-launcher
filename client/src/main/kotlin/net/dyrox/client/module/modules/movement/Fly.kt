package net.dyrox.client.module.modules.movement

import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.PlayerPreTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.PlayerUtil
import net.dyrox.client.util.mc

/**
 * - Vanilla: creative-style flight (double-tap not needed), with an adjustable speed.
 * - Motion: sets your velocity directly; jump/sneak to go up/down, otherwise you hover.
 * Anti-kick sinks you a tiny bit every second so servers that check "floating too long" stay calm.
 */
object Fly : Module("Fly", Category.MOVEMENT, "Lets you fly.") {
    enum class Mode(override val choiceName: String) : NamedChoice {
        VANILLA("Vanilla"),
        MOTION("Motion"),
    }

    private val mode by choice("Mode", Mode.VANILLA)
    private val speed by float("Speed", 1f, 0.1f..5f, 0.05f, "Horizontal speed (Vanilla: × creative speed, Motion: blocks/tick)")
    private val verticalSpeed by float("Vertical speed", 0.5f, 0.1f..3f, 0.05f).visibleIf { mode == Mode.MOTION }
    private val antiKick by boolean("Anti-kick", true)

    private var ticks = 0

    override val tag: String get() = mode.choiceName

    override fun onDisable() {
        val player = mc.player ?: return
        val abilities = player.abilities
        if (!player.isCreative && !player.isSpectator) {
            abilities.flying = false
            abilities.mayfly = false
        }
        abilities.setFlyingSpeed(0.05f)
        player.onUpdateAbilities()
    }

    @Suppress("unused")
    private val onTick = handler<PlayerPreTickEvent> {
        val player = mc.player ?: return@handler
        ticks++
        when (mode) {
            Mode.VANILLA -> {
                val abilities = player.abilities
                abilities.mayfly = true
                abilities.flying = true
                abilities.setFlyingSpeed(0.05f * speed)
            }
            Mode.MOTION -> {
                val options = mc.options
                val up = options.keyJump.isDown
                val down = options.keyShift.isDown
                val y = when {
                    up && !down -> verticalSpeed.toDouble()
                    down && !up -> -verticalSpeed.toDouble()
                    else -> 0.0
                }
                val direction = PlayerUtil.moveDirection(player)
                player.setDeltaMovement(direction.x * speed, y, direction.z * speed)
            }
        }
        if (antiKick && ticks % 40 == 0 && !player.onGround()) {
            val motion = player.deltaMovement
            player.setDeltaMovement(motion.x, motion.y - 0.04, motion.z)
        }
    }
}
