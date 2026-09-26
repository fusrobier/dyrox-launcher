package net.dyrox.client.util

import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet

/** Sending packets from modules. */
object Packets {
    private val unhooked = ThreadLocal.withInitial { false }

    /** True while [sendUnhooked] runs: PacketSendEvent is not posted for that packet. */
    val isUnhooked: Boolean get() = unhooked.get()

    fun send(packet: Packet<*>) {
        Minecraft.getInstance().connection?.send(packet)
    }

    /** Sends without posting PacketSendEvent, e.g. when Blink releases the packets it held. */
    fun sendUnhooked(packet: Packet<*>) {
        unhooked.set(true)
        try {
            send(packet)
        } finally {
            unhooked.set(false)
        }
    }
}
