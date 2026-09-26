package net.dyrox.client.module.modules.combat

import net.dyrox.client.combat.TargetType
import net.dyrox.client.combat.Targets
import net.dyrox.client.config.DyroxColor
import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.PostMotionEvent
import net.dyrox.client.event.PreMotionEvent
import net.dyrox.client.event.WorldGizmoEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.rotation.Rotation
import net.dyrox.client.rotation.RotationManager
import net.dyrox.client.util.PlayerUtil
import net.dyrox.client.util.format
import net.dyrox.client.util.mc
import net.minecraft.client.player.LocalPlayer
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.random.Random

/**
 * Attacks the best target in range. Rotations are silent (server-side only) by default, attacks wait
 * for a full weapon cooldown (1.9+ combat) or follow a CPS range.
 */
object KillAura : Module("KillAura", Category.COMBAT, "Automatically attacks entities around you.", defaultKey = "key.keyboard.r") {
    enum class RotationMode(override val choiceName: String) : NamedChoice {
        SILENT("Silent"),
        LOCK("Lock view"),
        NONE("None"),
    }

    enum class Timing(override val choiceName: String) : NamedChoice {
        COOLDOWN("Cooldown"),
        CPS("CPS"),
    }

    enum class Priority(override val choiceName: String) : NamedChoice {
        DISTANCE("Distance"),
        HEALTH("Health"),
        ANGLE("Angle"),
    }

    private val range by float("Range", 3.0f, 1f..6f, 0.05f, "Attack reach in blocks (vanilla survival: 3)")
    private val scanRange by float("Scan range", 4.5f, 1f..8f, 0.1f, "Start turning towards targets within this distance")
    private val targets by multiChoice("Targets", setOf(TargetType.PLAYERS, TargetType.HOSTILE), TargetType.entries)
    private val priority by choice("Priority", Priority.DISTANCE)
    private val rotations by choice("Rotations", RotationMode.SILENT)
    private val turnSpeed by floatRange("Turn speed", 60f..90f, 5f..180f, 1f, "Degrees per tick").visibleIf { rotations != RotationMode.NONE }
    private val timing by choice("Timing", Timing.COOLDOWN)
    private val cooldown by float("Cooldown", 0.95f, 0.5f..1f, 0.01f, "Attack strength needed (1 = full)").visibleIf { timing == Timing.COOLDOWN }
    private val cps by intRange("CPS", 8..12, 1..20).visibleIf { timing == Timing.CPS }
    private val fov by float("FOV", 360f, 30f..360f, 5f, "Only targets within this angle of your view")
    private val throughWalls by boolean("Through walls", false)
    private val invisibles by boolean("Invisibles", true)
    private val swing by boolean("Swing", true)
    private val targetEsp by boolean("Target ESP", true, "Draw a ring around the current target")
    private val espColor by color("ESP color", DyroxColor(0xFFB6FF3B.toInt()))

    var target: LivingEntity? = null
        private set
    private var nextAttackAt = 0L

    override val tag: String get() = range.format(1)

    override fun onDisable() {
        target = null
        RotationManager.release(this)
    }

    @Suppress("unused")
    private val onPreMotion = handler<PreMotionEvent> {
        val player = mc.player ?: return@handler
        target = findTarget(player)
        val current = target ?: return@handler
        val aim = aimRotation(player, current)
        when (rotations) {
            RotationMode.SILENT -> RotationManager.aim(this, aim, RotationManager.Priority.COMBAT, turnSpeed)
            RotationMode.LOCK -> {
                val next = Rotation(player.yRot, player.xRot).stepTowards(aim, Random.nextDouble(turnSpeed.start.toDouble(), turnSpeed.endInclusive.toDouble() + 0.001).toFloat())
                player.yRot = next.yaw
                player.xRot = next.pitch
            }
            RotationMode.NONE -> Unit
        }
    }

    @Suppress("unused")
    private val onPostMotion = handler<PostMotionEvent> {
        val player = mc.player ?: return@handler
        val current = target ?: return@handler
        if (mc.gui.screen() != null || player.isUsingItem) return@handler
        if (!inReach(player, current)) return@handler
        // Only hit when the rotation the server saw this tick actually faces the target.
        if (rotations != RotationMode.NONE && !isFacing(player, current)) return@handler
        if (!readyToAttack(player)) return@handler
        if (!Criticals.allowAttack(player)) return@handler
        // Sprinting hits never crit: tell the server we stopped for this one hit.
        val pauseSprint = Criticals.enabled && player.isSprinting
        if (pauseSprint) player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
        PlayerUtil.attack(player, current, swing)
        if (pauseSprint) player.connection.send(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING))
        attacks++
        scheduleNextAttack()
    }

    @Suppress("unused")
    private val onGizmos = handler<WorldGizmoEvent> { event ->
        val current = target ?: return@handler
        if (!targetEsp || !current.isAlive) return@handler
        val pos = current.getPosition(event.partialTicks)
        val bob = (Mth.sin((System.currentTimeMillis() % 2000) / 2000.0 * Mth.TWO_PI) + 1f) / 2f
        val y = pos.y + current.bbHeight * bob
        Gizmos.circle(Vec3(pos.x, y, pos.z), current.bbWidth * 0.8f, GizmoStyle.stroke(espColor.argb, 2f)).setAlwaysOnTop()
    }

    /** One line of state for DebugScript probes. */
    fun debugState(): String {
        val player = mc.player ?: return "no player"
        val current = target ?: return "no target"
        return "target=${current.type.description.string} reach=${inReach(player, current)} facing=${isFacing(player, current)} " +
            "strength=${player.getAttackStrengthScale(0.5f)} ready=${readyToAttack(player)} using=${player.isUsingItem} attacks=$attacks"
    }

    private var attacks = 0

    /** True when the next attack is at most ~2 ticks away and the target is in reach (Criticals starts its hop). */
    fun attackSoon(player: LocalPlayer): Boolean {
        val current = target ?: return false
        if (!inReach(player, current)) return false
        return when (timing) {
            Timing.COOLDOWN -> player.getAttackStrengthScale(2.5f) >= cooldown
            Timing.CPS -> nextAttackAt - System.currentTimeMillis() <= 100
        }
    }

    private fun readyToAttack(player: LocalPlayer): Boolean = when (timing) {
        Timing.COOLDOWN -> player.getAttackStrengthScale(0.5f) >= cooldown
        Timing.CPS -> System.currentTimeMillis() >= nextAttackAt
    }

    private fun scheduleNextAttack() {
        val clicks = if (cps.first >= cps.last) cps.first else Random.nextInt(cps.first, cps.last + 1)
        nextAttackAt = System.currentTimeMillis() + 1000L / clicks.coerceAtLeast(1)
    }

    private fun findTarget(player: LocalPlayer): LivingEntity? {
        val level = mc.level ?: return null
        val eye = player.eyePosition
        val view = Rotation(player.yRot, player.xRot)
        val candidates = level.entitiesForRendering().asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { Targets.isAttackable(it, targets, invisibles) }
            .filter { eye.distanceTo(Rotation.closestPoint(eye, it.boundingBox)) <= scanRange }
            .filter { fov >= 360f || view.angleTo(Rotation.between(eye, it.boundingBox.center)) <= fov / 2 }
            .filter { throughWalls || canSee(player, it) }
            .toList()
        return when (priority) {
            Priority.DISTANCE -> candidates.minByOrNull { it.distanceToSqr(player) }
            Priority.HEALTH -> candidates.minByOrNull { it.health }
            Priority.ANGLE -> candidates.minByOrNull { view.angleTo(Rotation.between(eye, it.boundingBox.center)) }
        }
    }

    /** Aim at the closest point of the hitbox, a little inside so the ray really hits it. */
    private fun aimRotation(player: LocalPlayer, target: LivingEntity): Rotation {
        val box = target.boundingBox.deflate(0.05)
        val eye = player.eyePosition
        return Rotation.between(eye, Rotation.closestPoint(eye, box))
    }

    private fun inReach(player: LocalPlayer, target: LivingEntity): Boolean {
        val eye = player.eyePosition
        return eye.distanceTo(Rotation.closestPoint(eye, target.boundingBox)) <= range
    }

    /** Whether the ray along the server rotation hits the target's hitbox within reach. */
    private fun isFacing(player: LocalPlayer, target: LivingEntity): Boolean {
        val rotation = RotationManager.effectiveRotation(player)
        val eye = player.eyePosition
        val look = Vec3.directionFromRotation(rotation.pitch, rotation.yaw)
        val end = eye.add(look.scale(range + 1.0))
        return target.boundingBox.inflate(0.1).clip(eye, end).isPresent
    }

    private fun canSee(player: LocalPlayer, target: LivingEntity): Boolean {
        val level = mc.level ?: return false
        val eye = player.eyePosition
        return listOf(target.eyePosition, target.boundingBox.center, target.position().add(0.0, 0.1, 0.0)).any { point ->
            level.clip(ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).type == HitResult.Type.MISS
        }
    }
}
