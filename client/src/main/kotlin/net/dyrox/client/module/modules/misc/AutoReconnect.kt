package net.dyrox.client.module.modules.misc

import net.dyrox.client.event.WorldChangeEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component

/**
 * Adds a "Reconnect" button to the disconnect screen and, while enabled, reconnects to the last
 * server automatically after a delay.
 */
object AutoReconnect : Module("AutoReconnect", Category.MISC, "Reconnects after being disconnected.") {
    private val delay by int("Delay", 5, 1..60, description = "Seconds before reconnecting")

    private var lastServer: ServerData? = null

    init {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is DisconnectedScreen) return@register
            val server = lastServer ?: return@register
            val reconnectAt = System.currentTimeMillis() + delay * 1000L
            val button = Button.builder(label(reconnectAt)) { reconnect(server) }.bounds(6, 6, 130, 20).build()
            Screens.getWidgets(screen).add(button)
            ScreenEvents.afterTick(screen).register {
                button.message = label(reconnectAt)
                if (enabled && System.currentTimeMillis() >= reconnectAt) reconnect(server)
            }
        }
    }

    // Remember the server on join: the disconnect screen no longer knows where we were.
    @Suppress("unused")
    private val onWorld = handler<WorldChangeEvent>(ignoreCondition = true) { event ->
        if (event.joined) lastServer = mc.currentServer?.takeIf { !it.isRealm }
    }

    private fun label(reconnectAt: Long): Component {
        val seconds = ((reconnectAt - System.currentTimeMillis()) / 1000L + 1).coerceAtLeast(0)
        return Component.literal(if (enabled) "Reconnect ($seconds)" else "Reconnect")
    }

    private fun reconnect(server: ServerData) {
        if (mc.gui.screen() !is DisconnectedScreen) return
        ConnectScreen.startConnecting(JoinMultiplayerScreen(TitleScreen()), mc, ServerAddress.parseString(server.ip), server, false, null)
    }
}
