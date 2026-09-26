package net.dyrox.client.module.modules.player

import net.dyrox.client.event.GameTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.ShulkerBoxMenu
import kotlin.random.Random

/** Shift-clicks everything out of chests, barrels and shulker boxes, then closes them. */
object ChestStealer : Module("ChestStealer", Category.PLAYER, "Empties containers into your inventory.") {
    private val delay by intRange("Delay", 60..120, 0..500, "Milliseconds between items")
    private val autoClose by boolean("Auto close", true)

    private var nextActionAt = 0L

    @Suppress("unused")
    private val onTick = handler<GameTickEvent> {
        val player = mc.player ?: return@handler
        val gameMode = mc.gameMode ?: return@handler
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return@handler
        val menu = screen.menu
        if (menu !is ChestMenu && menu !is ShulkerBoxMenu) return@handler
        val now = System.currentTimeMillis()
        if (now < nextActionAt) return@handler

        val containerSlots = menu.slots.filter { it.container !is Inventory }
        val slot = containerSlots.firstOrNull { it.hasItem() }
        if (slot == null) {
            if (autoClose) player.closeContainer()
            return@handler
        }
        // Stop when the inventory is full: the item would stay in the chest forever.
        if (player.inventory.getFreeSlot() == -1 && !canStack(player.inventory, slot.item)) {
            if (autoClose) player.closeContainer()
            return@handler
        }
        gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, player)
        nextActionAt = now + if (delay.first >= delay.last) delay.first.toLong() else Random.nextLong(delay.first.toLong(), delay.last + 1L)
    }

    private fun canStack(inventory: Inventory, stack: net.minecraft.world.item.ItemStack): Boolean =
        (0 until 36).any { i ->
            val own = inventory.getItem(i)
            net.minecraft.world.item.ItemStack.isSameItemSameComponents(own, stack) && own.count < own.maxStackSize
        }
}
