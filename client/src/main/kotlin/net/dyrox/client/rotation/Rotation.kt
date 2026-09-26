package net.dyrox.client.rotation

import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/** A look direction in Minecraft degrees: yaw 0 = south (+Z), pitch -90 = straight up. */
data class Rotation(val yaw: Float, val pitch: Float) {
    /** Total angle (degrees) between the two directions' yaw and pitch. */
    fun angleTo(other: Rotation): Float = hypot(Mth.wrapDegrees(other.yaw - yaw), other.pitch - pitch)

    /** Turns towards [target] by at most [maxStep] degrees, keeping yaw continuous (no wrap jumps). */
    fun stepTowards(target: Rotation, maxStep: Float): Rotation {
        val dYaw = Mth.wrapDegrees(target.yaw - yaw)
        val dPitch = target.pitch - pitch
        val distance = hypot(dYaw, dPitch)
        if (distance <= maxStep || distance < 1.0E-4f) return Rotation(yaw + dYaw, target.pitch.coerceIn(-90f, 90f))
        val scale = maxStep / distance
        return Rotation(yaw + dYaw * scale, (pitch + dPitch * scale).coerceIn(-90f, 90f))
    }

    companion object {
        /** The rotation that looks from [from] straight at [to]. */
        fun between(from: Vec3, to: Vec3): Rotation {
            val dx = to.x - from.x
            val dy = to.y - from.y
            val dz = to.z - from.z
            val yaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
            val pitch = -Math.toDegrees(atan2(dy, sqrt(dx * dx + dz * dz))).toFloat()
            return Rotation(Mth.wrapDegrees(yaw), pitch.coerceIn(-90f, 90f))
        }

        /** The point of [box] closest to [eye]: the shortest reach to an entity's hitbox. */
        fun closestPoint(eye: Vec3, box: AABB): Vec3 =
            Vec3(eye.x.coerceIn(box.minX, box.maxX), eye.y.coerceIn(box.minY, box.maxY), eye.z.coerceIn(box.minZ, box.maxZ))

        /**
         * Rounds the change [from] → [to] to what a real mouse can produce at [sensitivity] (0..1), like
         * vanilla MouseHandler. Anti-cheats flag rotations that are not multiples of this step.
         */
        fun applyMouseStep(from: Rotation, to: Rotation, sensitivity: Double): Rotation {
            val f = sensitivity * 0.6 + 0.2
            val step = (f * f * f * 8.0 * 0.15).toFloat()
            if (step <= 0f) return to
            val dYaw = Math.round(Mth.wrapDegrees(to.yaw - from.yaw) / step) * step
            val dPitch = Math.round((to.pitch - from.pitch) / step) * step
            return Rotation(from.yaw + dYaw, (from.pitch + dPitch).coerceIn(-90f, 90f))
        }
    }
}
