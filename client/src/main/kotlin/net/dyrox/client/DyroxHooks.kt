package net.dyrox.client

import net.dyrox.client.event.AttackEvent
import net.dyrox.client.event.Events
import net.dyrox.client.event.KeyEvent
import net.dyrox.client.event.PacketReceiveEvent
import net.dyrox.client.event.PacketSendEvent
import net.dyrox.client.event.PlayerPreTickEvent
import net.dyrox.client.event.PostMotionEvent
import net.dyrox.client.event.PreMotionEvent
import net.dyrox.client.event.WorldGizmoEvent
import net.dyrox.client.integration.LauncherBridge
import net.dyrox.client.module.modules.combat.Velocity
import net.dyrox.client.module.modules.movement.NoSlow
import net.dyrox.client.module.modules.movement.SafeWalk
import net.dyrox.client.module.modules.render.CameraClip
import net.dyrox.client.module.modules.render.Esp
import net.dyrox.client.module.modules.render.Fullbright
import net.dyrox.client.module.modules.render.NoHurtCam
import net.dyrox.client.module.modules.render.Zoom
import net.dyrox.client.module.modules.world.Scaffold
import net.dyrox.client.module.modules.world.Timer
import net.dyrox.client.render.CameraController
import net.dyrox.client.rotation.RotationManager
import net.dyrox.client.util.Packets
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.state.LightmapRenderState
import net.minecraft.network.protocol.Packet
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * The only entry points mixins call. Keeping mixin bodies to one line each makes them easy to audit
 * and to re-target when Minecraft changes.
 */
object DyroxHooks {
    // ---- Network --------------------------------------------------------------------------------

    /** @return true if the packet must not be sent. */
    @JvmStatic
    fun onPacketSend(packet: Packet<*>): Boolean = !Packets.isUnhooked && Events.post(PacketSendEvent(packet)).isCancelled

    /** @return true if the packet must be dropped. */
    @JvmStatic
    fun onPacketReceive(packet: Packet<*>): Boolean = Events.post(PacketReceiveEvent(packet)).isCancelled

    // ---- Input and window -----------------------------------------------------------------------

    @JvmStatic
    fun onKey(keyName: String, action: Int) {
        Events.post(KeyEvent(keyName, action, screenOpen = Minecraft.getInstance().gui.screen() != null))
    }

    @JvmStatic
    fun decorateTitle(title: String): String = title + LauncherBridge.titleSuffix(Minecraft.getInstance().user.name)

    // ---- Player ---------------------------------------------------------------------------------

    @JvmStatic
    fun onPlayerPreTick() {
        Events.post(PlayerPreTickEvent)
    }

    private var realYaw = 0f
    private var realPitch = 0f
    private var realOnGround = false
    private var swapped = false
    private var groundSwapped = false
    private var realY = 0.0
    private var ySwapped = false

    /** Start of LocalPlayer.sendPosition: let modules act, then swap in the server rotation, ground flag and height. */
    @JvmStatic
    fun preMotion(player: LocalPlayer) {
        val event = Events.post(PreMotionEvent())
        RotationManager.update(player)
        RotationManager.serverRotation?.let { rotation ->
            realYaw = player.yRot
            realPitch = player.xRot
            player.yRot = rotation.yaw
            player.xRot = rotation.pitch
            swapped = true
        }
        if (event.yOffset != 0.0) {
            realY = player.y
            player.setPosRaw(player.x, realY + event.yOffset, player.z)
            ySwapped = true
        }
        event.groundOverride?.let { ground ->
            realOnGround = player.onGround()
            player.setOnGround(ground)
            groundSwapped = true
        }
    }

    /** End of LocalPlayer.sendPosition: restore what the camera and physics use, then post-motion actions. */
    @JvmStatic
    fun postMotion(player: LocalPlayer) {
        if (swapped) {
            player.yRot = realYaw
            player.xRot = realPitch
            swapped = false
        }
        if (groundSwapped) {
            player.setOnGround(realOnGround)
            groundSwapped = false
        }
        if (ySwapped) {
            player.setPosRaw(player.x, realY, player.z)
            ySwapped = false
        }
        Events.post(PostMotionEvent)
    }

    @JvmStatic
    fun onAttack(target: Entity) {
        Events.post(AttackEvent(target))
    }

    /** Movement multiplier while using an item (eating, blocking, drawing a bow). */
    @JvmStatic
    fun itemUseSpeed(vanilla: Float): Float = if (NoSlow.enabled) maxOf(vanilla, NoSlow.speed) else vanilla

    /** Whether using an item stops sprinting. */
    @JvmStatic
    fun itemUseBlocksSprint(vanilla: Boolean): Boolean = vanilla && !(NoSlow.enabled && NoSlow.sprint)

    /** Sneak-like edge protection: true keeps the player from walking off block edges. */
    @JvmStatic
    fun stayOnEdge(player: Player, vanilla: Boolean): Boolean =
        vanilla || (player === Minecraft.getInstance().player && (SafeWalk.shouldHoldEdge() || Scaffold.shouldHoldEdge()))

    /** Knockback from the server (entity motion packet). */
    @JvmStatic
    fun entityVelocity(entity: Entity, motion: Vec3): Vec3 =
        if (entity === Minecraft.getInstance().player) Velocity.modify(motion) else motion

    @JvmStatic
    fun explosionKnockback(entity: Entity, impulse: Vec3): Vec3 =
        if (entity === Minecraft.getInstance().player) Velocity.modifyExplosion(impulse) else impulse

    // ---- Camera and rendering -------------------------------------------------------------------

    @JvmStatic
    fun cameraDistance(vanilla: Float): Float = CameraClip.modifyDistance(CameraController.modifyDistance(vanilla))

    /** True to skip the third-person camera's wall collision. */
    @JvmStatic
    fun cameraClipsThroughWalls(): Boolean = CameraClip.enabled

    @JvmStatic
    fun modifyFov(fov: Float): Float = Zoom.modifyFov(fov)

    @JvmStatic
    fun mouseSensitivityScale(): Double = Zoom.mouseScale()

    @JvmStatic
    fun shouldGlow(entity: Entity): Boolean = Esp.shouldGlow(entity)

    @JvmStatic
    fun glowColor(entity: Entity, vanilla: Int): Int = Esp.glowColor(entity) ?: vanilla

    /** Shows the server-side (spoofed) rotation on our own model in third person. */
    @JvmStatic
    fun modifyModel(entity: LivingEntity, state: LivingEntityRenderState, partialTicks: Float) {
        if (entity !== Minecraft.getInstance().player || state.isUpsideDown) return
        val (headYaw, bodyYaw, pitch) = RotationManager.modelRotation(partialTicks) ?: return
        state.bodyRot = bodyYaw
        state.yRot = net.minecraft.util.Mth.wrapDegrees(headYaw - bodyYaw)
        state.xRot = pitch
    }

    @JvmStatic
    fun cancelHurtCamera(): Boolean = NoHurtCam.enabled

    @JvmStatic
    fun gameSpeed(): Float = if (Timer.enabled) Timer.speed else 1f

    @JvmStatic
    fun modifyLightmap(state: LightmapRenderState) {
        if (Fullbright.enabled) {
            state.nightVisionEffectIntensity = 1f
            state.brightness = maxOf(state.brightness, 1f)
            state.darknessEffectScale = 0f
        }
    }

    @JvmStatic
    fun emitWorldGizmos(partialTicks: Float, camera: Camera) {
        if (Minecraft.getInstance().player == null) return
        Events.post(WorldGizmoEvent(partialTicks, camera))
    }
}
