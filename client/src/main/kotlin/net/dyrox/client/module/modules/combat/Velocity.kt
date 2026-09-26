package net.dyrox.client.module.modules.combat

import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.minecraft.world.phys.Vec3

/**
 * Scales the knockback the server applies to you (hits and explosions). 0 % = no knockback.
 * Applied in ClientPacketListener.handleSetEntityMotion and Entity.pushFromExplosion (see mixins).
 */
object Velocity : Module("Velocity", Category.COMBAT, "Reduces the knockback you take.") {
    private val horizontal by int("Horizontal", 0, 0..100, 5, "Percent of horizontal knockback kept")
    private val vertical by int("Vertical", 0, 0..100, 5, "Percent of vertical knockback kept")
    private val explosions by boolean("Explosions", true, "Also reduce explosion knockback")

    override val tag: String get() = "$horizontal% $vertical%"

    /** The new motion after a server velocity update. Vanilla replaces the motion with [motion]. */
    fun modify(motion: Vec3): Vec3 {
        if (!enabled) return motion
        val player = net.minecraft.client.Minecraft.getInstance().player ?: return motion
        val current = player.deltaMovement
        // Interpolate between keeping our own motion (0 %) and taking the server's (100 %).
        val h = horizontal / 100.0
        val v = vertical / 100.0
        return Vec3(
            current.x + (motion.x - current.x) * h,
            current.y + (motion.y - current.y) * v,
            current.z + (motion.z - current.z) * h,
        )
    }

    fun modifyExplosion(impulse: Vec3): Vec3 {
        if (!enabled || !explosions) return impulse
        return Vec3(impulse.x * horizontal / 100.0, impulse.y * vertical / 100.0, impulse.z * horizontal / 100.0)
    }
}
