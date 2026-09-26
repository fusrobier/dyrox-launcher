package net.dyrox.client.module.modules.combat

import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Items

/**
 * Keeps a Totem of Undying in your off hand: whenever it is gone, the next totem from your inventory
 * is swapped in (the same click as pressing F on it in the inventory).
 */
object AutoTotem : Module("AutoTotem", Category.COMBAT, "Keeps a totem in your off hand.") {
    private const val OFFHAND_BUTTON = 40

    private val delay by int("Delay", 2, 0..20, description = "Ticks between swaps")

    private var cooldown = 0

    override val tag: String
        get() = mc.player?.inventory?.let { inv -> (0 until inv.containerSize).sumOf { if (inv.getItem(it).item == Items.TOTEM_OF_UNDYING) inv.getItem(it).count else 0 } }?.toString() ?: ""

    @Suppress("unused")
    private val onTick = handler<PlayerTickEvent> {
        val player = mc.player ?: return@handler
        val gameMode = mc.gameMode ?: return@handler
        if (cooldown > 0) {
            cooldown--
            return@handler
        }
        if (player.offhandItem.item == Items.TOTEM_OF_UNDYING) return@handler
        // Inventory clicks only work in the player's own inventory menu (no chest open).
        val screen = mc.gui.screen()
        if (screen is AbstractContainerScreen<*> && screen !is InventoryScreen) return@handler
        if (!player.containerMenu.carried.isEmpty) return@handler
        val menu = player.inventoryMenu
        // Inventory menu slots 9..35 are the main inventory, 36..44 the hotbar.
        val slot = (9..44).firstOrNull { menu.slots[it].item.item == Items.TOTEM_OF_UNDYING } ?: return@handler
        gameMode.handleContainerInput(menu.containerId, slot, OFFHAND_BUTTON, ContainerInput.SWAP, player)
        cooldown = delay
    }
}
