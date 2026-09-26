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
import net.dyrox.client.util.PlayerUtil
import net.dyrox.client.util.mc
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.FallingBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/**
 * Places blocks under your feet as you walk (bridging). Picks a block from the hotbar, looks at the
 * face it builds on (silently by default) and clicks it like a real right click. Hold jump to tower.
 */
object Scaffold : Module("Scaffold", Category.WORLD, "Places blocks under you while you walk.") {
    enum class RotationMode(override val choiceName: String) : NamedChoice {
        SILENT("Silent"),
        NONE("None"),
    }

    private val rotations by choice("Rotations", RotationMode.SILENT)
    private val turnSpeed by floatRange("Turn speed", 120f..180f, 20f..180f, 1f).visibleIf { rotations == RotationMode.SILENT }
    private val safeWalk by boolean("Safe walk", true, "Stop at edges while no block could be placed")
    private val swing by boolean("Swing", true)
    private val restoreSlot by boolean("Restore slot", true, "Switch back to your slot when disabled")
    private val showTarget by boolean("Show target", true, "Outline the block about to be placed")

    private class Placement(val target: BlockPos, val hit: BlockHitResult)

    private var placement: Placement? = null
    private var previousSlot = -1

    override fun onEnable() {
        previousSlot = mc.player?.inventory?.selectedSlot ?: -1
    }

    override fun onDisable() {
        placement = null
        RotationManager.release(this)
        val player = mc.player ?: return
        if (restoreSlot && previousSlot in 0..8) player.inventory.selectedSlot = previousSlot
        previousSlot = -1
    }

    /** SafeWalk hook: hold the edge while on the ground. */
    fun shouldHoldEdge(): Boolean = enabled && safeWalk && mc.player?.onGround() == true && !mc.options.keyJump.isDown

    @Suppress("unused")
    private val onPreMotion = handler<PreMotionEvent> {
        val player = mc.player ?: return@handler
        placement = null
        if (PlayerUtil.findHotbar(player, ::isPlaceable) == null) return@handler
        val next = findPlacement(player) ?: return@handler
        placement = next
        if (rotations == RotationMode.SILENT) {
            RotationManager.aim(this, Rotation.between(player.eyePosition, next.hit.location), RotationManager.Priority.BUILD, turnSpeed)
        }
    }

    @Suppress("unused")
    private val onPostMotion = handler<PostMotionEvent> {
        val player = mc.player ?: return@handler
        val gameMode = mc.gameMode ?: return@handler
        val next = placement ?: return@handler
        if (mc.gui.screen() != null) return@handler
        // Wait until the server rotation faces the block, like a real player would.
        if (rotations == RotationMode.SILENT) {
            val wanted = Rotation.between(player.eyePosition, next.hit.location)
            if (RotationManager.effectiveRotation(player).angleTo(wanted) > 25f) return@handler
        }
        val slot = PlayerUtil.findHotbar(player, ::isPlaceable) ?: return@handler
        player.inventory.selectedSlot = slot
        val stack = player.mainHandItem
        val result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, next.hit)
        if (result is InteractionResult.Success && swing) player.swing(InteractionHand.MAIN_HAND, stack.interactAnimation, false)
    }

    @Suppress("unused")
    private val onGizmos = handler<WorldGizmoEvent> {
        val next = placement ?: return@handler
        if (showTarget) WorldShapes.box(AABB(next.target), 0xFFB6FF3B.toInt(), 40)
    }

    private fun isPlaceable(stack: ItemStack): Boolean {
        val item = stack.item as? BlockItem ?: return false
        if (stack.isEmpty) return false
        val block = item.block
        // Falling blocks (sand, gravel) and non-full blocks make bad bridges.
        if (block is FallingBlock) return false
        return block.defaultBlockState().isCollisionShapeFullBlock(mc.level ?: return false, BlockPos.ZERO)
    }

    private fun findPlacement(player: LocalPlayer): Placement? {
        val level = mc.level ?: return null
        // Under our feet, or (when that is solid, e.g. held at the edge by safe walk) half a block ahead.
        val ahead = PlayerUtil.moveDirection(player).scale(0.5)
        val below = listOf(
            BlockPos.containing(player.x, player.y - 1.0, player.z),
            BlockPos.containing(player.x + ahead.x, player.y - 1.0, player.z + ahead.z),
        ).firstOrNull { level.getBlockState(it).canBeReplaced() } ?: return null
        // Build directly under us; if nothing to attach to, try the neighbours of that spot (diagonal gaps).
        val candidates = listOf(below) + Direction.Plane.HORIZONTAL.map { below.relative(it) }
            .sortedBy { Vec3.atCenterOf(it).distanceToSqr(player.x, player.y - 1.0, player.z) }
        for (target in candidates) {
            if (!level.getBlockState(target).canBeReplaced()) continue
            val hit = attachTo(player, target) ?: continue
            return Placement(target, hit)
        }
        return null
    }

    /** A click on a solid neighbour of [target] that places a block into [target]. */
    private fun attachTo(player: LocalPlayer, target: BlockPos): BlockHitResult? {
        val level = mc.level ?: return null
        val eye = player.eyePosition
        val range = player.blockInteractionRange()
        return Direction.entries
            .asSequence()
            .map { direction -> target.relative(direction) to direction.opposite }
            .filter { (neighbour, _) ->
                val state = level.getBlockState(neighbour)
                !state.canBeReplaced() && state.getMenuProvider(level, neighbour) == null
            }
            .map { (neighbour, face) ->
                val hitPos = Vec3.atCenterOf(neighbour).add(Vec3(face.unitVec3.x * 0.5, face.unitVec3.y * 0.5, face.unitVec3.z * 0.5))
                BlockHitResult(hitPos, face, neighbour, false)
            }
            .filter { it.location.distanceTo(eye) <= range }
            .minByOrNull { it.location.distanceToSqr(eye) }
    }
}
