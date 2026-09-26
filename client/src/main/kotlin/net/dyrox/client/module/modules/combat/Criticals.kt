package net.dyrox.client.module.modules.combat

import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.PreMotionEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc
import net.minecraft.client.player.LocalPlayer

/**
 * Makes KillAura's hits critical. The server grants a crit when the attacker is falling
 * (fallDistance > 0), not on ground, not sprinting, not in water and not climbing.
 *
 * - Packet: reports a tiny hop over two ticks (0.0625 up with "not on ground", then back down) and lets
 *   KillAura hit on the second tick. 26.x servers accept only one position packet per client tick
 *   (older clients sent the whole hop at once and now get kicked), so it rides on the normal packets.
 * - Jump: really jumps and lets KillAura hit on the way down.
 *
 * Manual clicks are not delayed, so they only crit when you jump yourself.
 */
object Criticals : Module("Criticals", Category.COMBAT, "Makes KillAura's hits critical.") {
    enum class Mode(override val choiceName: String) : NamedChoice {
        PACKET("Packet"),
        JUMP("Jump"),
    }

    private const val HOP = 0.0625

    private val mode by choice("Mode", Mode.PACKET)

    /** Packet mode: 0 idle, 1 hop reported this tick, 2 descending (server sees falling) = crit tick. */
    private var stage = 0

    override val tag: String get() = mode.choiceName

    override fun onDisable() {
        stage = 0
    }

    /** Whether a crit is possible for the player right now (fluids, ladders, riding and flying rule it out). */
    private fun critPossible(player: LocalPlayer): Boolean =
        !player.isInWater && !player.isInLava && !player.onClimbable() && !player.isPassenger && !player.abilities.flying

    /**
     * KillAura calls this before attacking: false means "wait, the next tick will be a crit".
     * Always true when disabled or when no crit is possible.
     */
    fun allowAttack(player: LocalPlayer): Boolean {
        if (!enabled || !critPossible(player)) return true
        return when (mode) {
            Mode.PACKET -> !player.onGround() || stage == 2
            Mode.JUMP -> !player.onGround() && (player.fallDistance > 0.0 || player.deltaMovement.y < 0)
        }
    }

    // Before KillAura (priority 10): drive the hop with last tick's target.
    @Suppress("unused")
    private val onPreMotion = handler<PreMotionEvent>(priority = 10) { event ->
        val player = mc.player ?: return@handler
        val attacking = KillAura.enabled && KillAura.target != null && KillAura.attackSoon(player)
        if (!critPossible(player)) {
            stage = 0
            return@handler
        }
        when (mode) {
            Mode.PACKET -> stage = when {
                stage == 1 -> {
                    // Back at the real height, still "in the air": the server now counts a 0.0625 fall.
                    event.groundOverride = false
                    2
                }
                attacking && player.onGround() && stage == 0 -> {
                    event.yOffset = HOP
                    event.groundOverride = false
                    1
                }
                else -> 0
            }
            Mode.JUMP -> if (attacking && player.onGround() && !mc.options.keyJump.isDown) player.jumpFromGround()
        }
    }
}
