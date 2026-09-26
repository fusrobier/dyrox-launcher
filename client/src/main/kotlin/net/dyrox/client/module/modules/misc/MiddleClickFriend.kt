package net.dyrox.client.module.modules.misc

import net.dyrox.client.combat.Friends
import net.dyrox.client.event.GameTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.ChatOutput
import net.dyrox.client.util.mc
import net.minecraft.world.entity.player.Player

/** Middle-click a player to add or remove them as a friend (combat modules ignore friends). */
object MiddleClickFriend : Module("MiddleClickFriend", Category.MISC, "Middle-click players to befriend them.", defaultEnabled = true, hidden = true) {
    private var wasDown = false

    @Suppress("unused")
    private val onTick = handler<GameTickEvent> {
        val down = mc.options.keyPickItem.isDown
        val clicked = down && !wasDown
        wasDown = down
        if (!clicked || mc.gui.screen() != null) return@handler
        val target = mc.crosshairPickEntity as? Player ?: return@handler
        val name = target.gameProfile.name
        if (Friends.isFriend(name)) {
            Friends.remove(name)
            ChatOutput.info("Removed $name from your friends.")
        } else {
            Friends.add(name)
            ChatOutput.info("Added $name to your friends.")
        }
    }
}
