package net.dyrox.client.module.modules.player

import net.dyrox.client.event.PlayerPreTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.module.modules.combat.KillAura
import net.dyrox.client.util.PlayerUtil
import net.dyrox.client.util.mc
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Eats from the hotbar when hunger drops to the threshold: selects the food, holds right click
 * until done, then goes back to the previous slot. Skips food with bad effects.
 */
object AutoEat : Module("AutoEat", Category.PLAYER, "Eats automatically when hungry.") {
    private val hunger by int("Hunger", 14, 1..19, description = "Start eating at or below this food level (20 = full)")
    private val pauseInCombat by boolean("Pause in combat", true, "Don't eat while KillAura has a target")
    private val goldenApples by boolean("Golden apples", false, "Also eat (enchanted) golden apples")

    private val badFood = setOf(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH, Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW)
    private val preciousFood = setOf(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE)

    private var previousSlot = -1
    private var eating = false

    @Suppress("unused")
    private val onTick = handler<PlayerPreTickEvent> {
        val player = mc.player ?: return@handler
        val wantsFood = player.foodData.foodLevel <= hunger && !player.isCreative && mc.gui.screen() == null &&
            !(pauseInCombat && KillAura.enabled && KillAura.target != null)
        if (!wantsFood) {
            stop()
            return@handler
        }
        val slot = PlayerUtil.findHotbar(player, ::isGoodFood)
        if (slot == null) {
            stop()
            return@handler
        }
        if (!eating) {
            previousSlot = player.inventory.selectedSlot
            eating = true
        }
        player.inventory.selectedSlot = slot
        mc.options.keyUse.setDown(true)
    }

    private fun isGoodFood(stack: ItemStack): Boolean =
        stack.has(DataComponents.FOOD) && stack.item !in badFood && (goldenApples || stack.item !in preciousFood)

    private fun stop() {
        if (!eating) return
        eating = false
        mc.options.keyUse.setDown(false)
        val player = mc.player ?: return
        if (previousSlot in 0..8) player.inventory.selectedSlot = previousSlot
        previousSlot = -1
    }

    override fun onDisable() = stop()
}
