package net.dyrox.client.rotation

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import kotlin.random.Random

/**
 * Server-side ("silent") rotations. Modules ask for a rotation every tick with [aim]; the highest
 * priority request wins. Just before the movement packet the player's yaw/pitch are swapped for the
 * server rotation and restored right after, so the camera never moves.
 *
 * Turning is limited to the request's speed (degrees per tick) and snapped to mouse steps. When no
 * module aims any more, the server rotation glides back to the camera and then releases.
 */
object RotationManager {
    object Priority {
        const val FUN = 0
        const val NORMAL = 50
        const val COMBAT = 100
        const val BUILD = 150
    }

    private class Request(val owner: Any, val rotation: Rotation, val priority: Int, val speed: ClosedFloatingPointRange<Float>, var ticks: Int)

    private var request: Request? = null

    /** The rotation sent this tick, or null while the server simply follows the camera. */
    var serverRotation: Rotation? = null
        private set

    /** For rendering the player model with the spoofed rotation (interpolated between ticks). */
    private var previous: Rotation? = null
    private var bodyYaw = 0f
    private var previousBodyYaw = 0f

    val isActive: Boolean get() = serverRotation != null

    /**
     * Requests [rotation] for [keepTicks] ticks. Higher [priority] wins; a lower one is ignored while a
     * higher request is active. [speed] is the degrees per tick range (random within).
     */
    fun aim(owner: Any, rotation: Rotation, priority: Int, speed: ClosedFloatingPointRange<Float> = 180f..180f, keepTicks: Int = 1): Boolean {
        val current = request
        if (current != null && current.owner !== owner && current.priority > priority) return false
        request = Request(owner, rotation, priority, speed, keepTicks)
        return true
    }

    fun release(owner: Any) {
        if (request?.owner === owner) request = null
    }

    fun isAimedBy(owner: Any): Boolean = request?.owner === owner

    /** The rotation the server will see this tick, whether spoofed or not. */
    fun effectiveRotation(player: LocalPlayer): Rotation = serverRotation ?: Rotation(player.yRot, player.xRot)

    // Called once per tick from DyroxHooks.preMotion, after modules posted their requests.
    internal fun update(player: LocalPlayer) {
        previous = serverRotation
        previousBodyYaw = bodyYaw
        val real = Rotation(player.yRot, player.xRot)
        val from = serverRotation ?: real
        val current = request
        serverRotation = if (current != null) {
            val speed = if (current.speed.start >= current.speed.endInclusive) current.speed.start
            else Random.nextDouble(current.speed.start.toDouble(), current.speed.endInclusive.toDouble()).toFloat()
            val stepped = from.stepTowards(current.rotation, speed)
            if (--current.ticks <= 0) request = null
            Rotation.applyMouseStep(from, stepped, Minecraft.getInstance().options.sensitivity().get())
        } else if (serverRotation != null) {
            // Glide back to where the camera looks, then stop spoofing.
            val back = from.stepTowards(real, 45f)
            if (back.angleTo(real) < 1f) null else back
        } else {
            null
        }
        // Keep the spoofed yaw within ±180° of the camera: same direction, but no ever-growing numbers.
        serverRotation = serverRotation?.let { Rotation(real.yaw + Mth.wrapDegrees(it.yaw - real.yaw), it.pitch) }
        serverRotation?.let { rotation ->
            if (previous == null) bodyYaw = player.yBodyRot
            // The body follows the head like vanilla: never more than 50° apart.
            val diff = Mth.wrapDegrees(rotation.yaw - bodyYaw)
            bodyYaw = rotation.yaw - diff.coerceIn(-50f, 50f)
            if (previous == null) previousBodyYaw = bodyYaw
        }
    }

    /** Head yaw, body yaw and pitch for the local player's model, or null when not spoofing. */
    fun modelRotation(partialTicks: Float): FloatArray? {
        val now = serverRotation ?: return null
        val before = previous ?: now
        return floatArrayOf(
            Mth.rotLerp(partialTicks, before.yaw, now.yaw),
            Mth.rotLerp(partialTicks, previousBodyYaw, bodyYaw),
            Mth.lerp(partialTicks, before.pitch, now.pitch),
        )
    }

    fun reset() {
        request = null
        serverRotation = null
        previous = null
    }
}
