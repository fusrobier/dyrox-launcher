package net.dyrox.client.util

import net.dyrox.client.rotation.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

val mc: Minecraft get() = Minecraft.getInstance()

/** Common player actions, done the way vanilla does them so servers see normal packets. */
object PlayerUtil {
    /** Attacks like a left click: attack packet, swing, punch packet. */
    fun attack(player: LocalPlayer, target: Entity, swing: Boolean = true) {
        val gameMode = mc.gameMode ?: return
        val animation = player.mainHandItem.attackAnimation
        gameMode.attack(player, target)
        if (swing) player.swing(InteractionHand.MAIN_HAND, animation, false)
        player.connection.send(ServerboundPunchPacket.INSTANCE)
    }

    /** Whether the player is pressing any movement key. */
    fun isMoving(player: LocalPlayer): Boolean = player.input.moveVector.lengthSquared() > 1.0E-5f

    /**
     * World-space direction of the movement keys (yaw from the camera, not a spoofed rotation),
     * normalised; zero when no key is held.
     */
    fun moveDirection(player: LocalPlayer): Vec3 {
        val input = player.input.moveVector
        if (input.lengthSquared() < 1.0E-5f) return Vec3.ZERO
        val yaw = Math.toRadians(player.yRot.toDouble())
        val forward = input.y.toDouble()
        val strafe = input.x.toDouble()
        val x = strafe * cos(yaw) - forward * sin(yaw)
        val z = forward * cos(yaw) + strafe * sin(yaw)
        return Vec3(x, 0.0, z).normalize()
    }

    /** Sets horizontal speed in the movement-key direction, keeping vertical motion. */
    fun strafe(player: LocalPlayer, speed: Double) {
        val direction = moveDirection(player)
        val motion = player.deltaMovement
        player.setDeltaMovement(direction.x * speed, motion.y, direction.z * speed)
    }

    /** Hotbar slot (0..8) of the first stack matching [predicate], preferring the selected slot. */
    fun findHotbar(player: LocalPlayer, predicate: (ItemStack) -> Boolean): Int? {
        val inventory = player.inventory
        if (predicate(inventory.getItem(inventory.selectedSlot))) return inventory.selectedSlot
        return (0..8).firstOrNull { predicate(inventory.getItem(it)) }
    }

    fun eyePosition(player: LocalPlayer): Vec3 = player.eyePosition

    /** Rotation the server sees this tick (spoofed or real). */
    fun serverRotation(player: LocalPlayer) = RotationManager.effectiveRotation(player)
}
