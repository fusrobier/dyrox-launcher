package net.dyrox.client

import net.dyrox.client.event.Events
import net.dyrox.client.event.KeyEvent
import net.dyrox.client.event.PacketReceiveEvent
import net.dyrox.client.event.PacketSendEvent
import net.dyrox.client.integration.LauncherBridge
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet

/**
 * The only entry points mixins call. Keeping mixin bodies to one line each makes them easy to audit
 * and to re-target when Minecraft changes.
 */
object DyroxHooks {
    /** @return true if the packet must not be sent. */
    @JvmStatic
    fun onPacketSend(packet: Packet<*>): Boolean = Events.post(PacketSendEvent(packet)).isCancelled

    /** @return true if the packet must be dropped. */
    @JvmStatic
    fun onPacketReceive(packet: Packet<*>): Boolean = Events.post(PacketReceiveEvent(packet)).isCancelled

    @JvmStatic
    fun onKey(keyName: String, action: Int) {
        Events.post(KeyEvent(keyName, action, screenOpen = Minecraft.getInstance().gui.screen() != null))
    }

    @JvmStatic
    fun decorateTitle(title: String): String = title + LauncherBridge.titleSuffix(Minecraft.getInstance().user.name)
}
