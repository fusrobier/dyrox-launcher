package net.dyrox.client.module.modules.movement

import com.mojang.blaze3d.platform.InputConstants
import net.dyrox.client.event.PlayerPreTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.ui.clickgui.ClickGuiScreen
import net.dyrox.client.util.mc
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen

/**
 * Walk, jump and sprint while an inventory, a container or the ClickGUI is open. Screens with text
 * input (chat, signs, creative search, ClickGUI search) are left alone.
 */
object InventoryMove : Module("InventoryMove", Category.MOVEMENT, "Move while menus are open.") {
    private val sneak by boolean("Sneak", false)

    @Suppress("unused")
    private val onTick = handler<PlayerPreTickEvent> {
        val screen = mc.gui.screen() ?: return@handler
        val allowed = when (screen) {
            is CreativeModeInventoryScreen -> false
            is AbstractContainerScreen<*> -> true
            is ClickGuiScreen -> !screen.isTyping
            else -> false
        }
        if (!allowed) return@handler
        val options = mc.options
        val keys = mutableListOf(options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keySprint)
        if (sneak) keys += options.keyShift
        keys.forEach(::syncWithKeyboard)
    }

    /** Presses [key] exactly while its physical keyboard key is held. */
    private fun syncWithKeyboard(key: KeyMapping) {
        val bound = InputConstants.getKey(key.saveString())
        if (bound.type != InputConstants.Type.KEYBOARD) return
        key.setDown(InputConstants.isKeyDown(bound.value))
    }
}
