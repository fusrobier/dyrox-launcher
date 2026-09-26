package net.dyrox.client.module.modules.world

import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.PostMotionEvent
import net.dyrox.client.event.PreMotionEvent
import net.dyrox.client.event.WorldGizmoEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.render.WorldShapes
import net.dyrox.client.rotation.Rotation
import net.dyrox.client.rotation.RotationManager
import net.dyrox.client.util.mc
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/**
 * Breaks blocks around you. In creative many blocks break per tick; in survival it mines the nearest
 * block with normal mining speed (combine with FastBreak and AutoTool).
 */
object Nuker : Module("Nuker", Category.WORLD, "Breaks blocks around you.") {
    enum class Shape(override val choiceName: String) : NamedChoice {
        SPHERE("Sphere"),
        FLAT("Flat"),
    }

    private val range by float("Range", 4.5f, 1f..6f, 0.1f)
    private val shape by choice("Shape", Shape.FLAT, description = "Flat never digs below your feet")
    private val perTick by int("Blocks per tick", 4, 1..32, description = "Creative only")
    private val rotate by boolean("Rotate", true, "Look at the block being mined (silently)")

    private var target: BlockPos? = null

    override fun onDisable() {
        target = null
        RotationManager.release(this)
        mc.gameMode?.stopDestroyBlock()
    }

    @Suppress("unused")
    private val onPreMotion = handler<PreMotionEvent> {
        val player = mc.player ?: return@handler
        val current = target?.takeIf { isBreakable(player, it) }
        target = current ?: candidates(player).firstOrNull()
        val next = target ?: return@handler
        if (rotate && !player.isCreative) {
            RotationManager.aim(this, Rotation.between(player.eyePosition, Vec3.atCenterOf(next)), RotationManager.Priority.NORMAL, 60f..90f)
        }
    }

    @Suppress("unused")
    private val onPostMotion = handler<PostMotionEvent> {
        val player = mc.player ?: return@handler
        val gameMode = mc.gameMode ?: return@handler
        if (mc.gui.screen() != null) return@handler
        if (player.isCreative) {
            // Creative breaks instantly on the first click.
            for (pos in candidates(player).take(perTick)) {
                gameMode.startDestroyBlock(pos, faceFor(player, pos))
            }
            player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
            target = null
            return@handler
        }
        val next = target ?: return@handler
        if (gameMode.continueDestroyBlock(next, faceFor(player, next))) {
            player.swing(InteractionHand.MAIN_HAND, player.mainHandItem.attackAnimation, false)
            player.connection.send(ServerboundPunchPacket.INSTANCE)
        }
    }

    @Suppress("unused")
    private val onGizmos = handler<WorldGizmoEvent> {
        val next = target ?: return@handler
        WorldShapes.box(AABB(next), 0xFFFF5A5A.toInt(), 30)
    }

    private fun candidates(player: LocalPlayer): List<BlockPos> {
        val eye = player.eyePosition
        val r = ceil(range).toInt()
        val feetY = player.blockY
        val origin = BlockPos.containing(eye)
        val result = ArrayList<BlockPos>()
        for (pos in BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (shape == Shape.FLAT && pos.y < feetY) continue
            if (Vec3.atCenterOf(pos).distanceTo(eye) > range) continue
            if (isBreakable(player, pos)) result += pos.immutable()
        }
        result.sortBy { Vec3.atCenterOf(it).distanceToSqr(eye) }
        return result
    }

    private fun isBreakable(player: LocalPlayer, pos: BlockPos): Boolean {
        val level = mc.level ?: return false
        val state = level.getBlockState(pos)
        if (state.isAir || !state.fluidState.isEmpty) return false
        if (Vec3.atCenterOf(pos).distanceTo(player.eyePosition) > range) return false
        if (shape == Shape.FLAT && pos.y < player.blockY) return false
        // Bedrock, barriers, end portal frames...
        return state.getDestroySpeed(level, pos) >= 0f
    }

    private fun faceFor(player: LocalPlayer, pos: BlockPos): Direction =
        Direction.getApproximateNearest(player.eyePosition.subtract(Vec3.atCenterOf(pos)))
}
