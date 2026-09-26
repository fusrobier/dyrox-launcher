package net.dyrox.client.event

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.protocol.Packet

/** Start of every client tick (20/s), in menus too. */
object GameTickEvent : Event

/** End of every client tick, only while a world is loaded. */
object PlayerTickEvent : Event

/**
 * A keyboard key changed state. [keyName] is Minecraft's name (`key.keyboard.r`), which is what
 * keybinds store; [action] is [PRESS], [RELEASE] or [REPEAT].
 */
class KeyEvent(val keyName: String, val action: Int, val screenOpen: Boolean) : Event {
    companion object {
        const val RELEASE = 0
        const val PRESS = 1
        const val REPEAT = -1
    }
}

/** A packet is about to be sent to the server. Runs on the network thread or the client thread. */
class PacketSendEvent(val packet: Packet<*>) : CancellableEvent()

/** A packet arrived from the server. Runs on the Netty thread, before Minecraft handles it. */
class PacketReceiveEvent(val packet: Packet<*>) : CancellableEvent()

/** The player sent a chat message that isn't a Dyrox command. Cancel to swallow it. */
class ChatSendEvent(val message: String) : CancellableEvent()

/** HUD pass; draw with [graphics] (26.x extracts render state rather than drawing immediately). */
class Render2DEvent(val graphics: GuiGraphicsExtractor, val delta: DeltaTracker) : Event

/** End of the main world render pass. */
class WorldRenderEvent(val context: LevelRenderContext) : Event

/** Joined a world or server ([joined] = true), or left it. */
class WorldChangeEvent(val joined: Boolean) : Event

/** The client is shutting down; last chance to save. */
object ClientShutdownEvent : Event

/** Start of the local player's tick, before input and movement. Only while in a world. */
object PlayerPreTickEvent : Event

/**
 * Just before the movement packet is built (after this tick's movement). Modules pick rotations here
 * (see RotationManager) and may spoof the ground flag of this tick's packet with [groundOverride].
 */
class PreMotionEvent : Event {
    var groundOverride: Boolean? = null

    /** Reported height offset for this tick's packet (Criticals' hop); the real position is untouched. */
    var yOffset: Double = 0.0
}

/** The movement packet (with this tick's server rotation) was sent: attack, place, break here. */
object PostMotionEvent : Event

/** The player is about to attack [target] (vanilla click or a module), before the attack packet. */
class AttackEvent(val target: net.minecraft.world.entity.Entity) : Event

/**
 * World-space debug shapes for this frame: draw with `net.minecraft.gizmos.Gizmos` (lines, boxes,
 * text). Positions should be interpolated with [partialTicks].
 */
class WorldGizmoEvent(val partialTicks: Float, val camera: net.minecraft.client.Camera) : Event
