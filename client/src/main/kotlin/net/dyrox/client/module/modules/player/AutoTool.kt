package net.dyrox.client.module.modules.player

import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/** Selects the fastest hotbar tool for the block you are breaking, and optionally switches back. */
object AutoTool : Module("AutoTool", Category.PLAYER, "Picks the best tool while mining.") {
    private val switchBack by boolean("Switch back", true, "Return to the previous slot when you stop mining")

    private var previousSlot = -1

    @Suppress("unused")
    private val onTick = handler<PlayerTickEvent> {
        val player = mc.player ?: return@handler
        val level = mc.level ?: return@handler
        val hit = mc.hitResult
        val mining = mc.options.keyAttack.isDown && mc.gui.screen() == null && hit is BlockHitResult && hit.type == HitResult.Type.BLOCK
        if (!mining) {
            if (switchBack && previousSlot >= 0) {
                player.inventory.selectedSlot = previousSlot
                previousSlot = -1
            }
            return@handler
        }
        val state = level.getBlockState(hit.blockPos)
        if (state.isAir) return@handler
        val inventory = player.inventory
        val best = (0..8).maxByOrNull { inventory.getItem(it).getDestroySpeed(state) } ?: return@handler
        if (best == inventory.selectedSlot) return@handler
        if (inventory.getItem(best).getDestroySpeed(state) <= inventory.getItem(inventory.selectedSlot).getDestroySpeed(state)) return@handler
        if (previousSlot < 0) previousSlot = inventory.selectedSlot
        inventory.selectedSlot = best
    }

    override fun onDisable() {
        previousSlot = -1
    }
}
