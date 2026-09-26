package net.dyrox.client.module.modules.`fun`

import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.PreMotionEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.render.CameraController
import net.dyrox.client.rotation.Rotation
import net.dyrox.client.rotation.RotationManager
import net.dyrox.client.util.mc
import net.minecraft.util.Mth
import kotlin.random.Random

/**
 * Spinbot / anti-aim: sends rotations that have nothing to do with where you look. Other players see
 * your head spin or jitter; your camera stays put. Combat and building rotations (KillAura, Scaffold)
 * take priority, so it can stay on while you fight.
 *
 * "Third person" glides the camera out behind you while it runs (and back in when you stop) so you can
 * watch your own model; the model shows the spoofed rotations.
 */
object AntiAim : Module("AntiAim", Category.FUN, "Spinbot and anti-aim with a smooth third-person view.") {
    enum class YawMode(override val choiceName: String) : NamedChoice {
        SPIN("Spin"),
        JITTER("Jitter"),
        BACKWARDS("Backwards"),
        RANDOM("Random"),
        OFF("Off"),
    }

    enum class PitchMode(override val choiceName: String) : NamedChoice {
        DOWN("Down"),
        UP("Up"),
        ZERO("Zero"),
        JITTER("Jitter"),
        RANDOM("Random"),
        OFF("Off"),
    }

    private val yawMode by choice("Yaw", YawMode.SPIN)
    private val spinSpeed by float("Spin speed", 35f, 5f..90f, 1f, "Degrees per tick").visibleIf { yawMode == YawMode.SPIN }
    private val jitterAngle by float("Jitter angle", 90f, 10f..180f, 5f).visibleIf { yawMode == YawMode.JITTER || pitchMode == PitchMode.JITTER }
    private val pitchMode by choice("Pitch", PitchMode.DOWN)
    private val thirdPersonSetting = boolean("Third person", true, "Smoothly switch to a third-person view while active")
    private val thirdPerson by thirdPersonSetting
    private val distanceSetting = float("Camera distance", 4f, 2f..10f, 0.25f).visibleIf { thirdPerson }
    private val cameraDistance by distanceSetting
    private val cameraSpeed by int("Camera glide", 450, 100..1500, 50, "Milliseconds for the camera to glide in/out").visibleIf { thirdPerson }

    private var spinYaw = 0f
    private var flip = false

    override val tag: String get() = yawMode.choiceName

    init {
        thirdPersonSetting.onChange { on ->
            if (!enabled) return@onChange
            if (on) CameraController.enter(this, cameraDistance, cameraSpeed.toFloat()) else CameraController.exit(this)
        }
        distanceSetting.onChange { CameraController.retarget(it) }
    }

    override fun onEnable() {
        spinYaw = mc.player?.yRot ?: 0f
        if (thirdPerson) CameraController.enter(this, cameraDistance, cameraSpeed.toFloat())
    }

    override fun onDisable() {
        RotationManager.release(this)
        CameraController.exit(this)
    }

    @Suppress("unused")
    private val onPreMotion = handler<PreMotionEvent> {
        val player = mc.player ?: return@handler
        flip = !flip
        val baseYaw = player.yRot
        val yaw = when (yawMode) {
            YawMode.SPIN -> {
                spinYaw = Mth.wrapDegrees(spinYaw + spinSpeed)
                spinYaw
            }
            YawMode.JITTER -> baseYaw + 180f + if (flip) jitterAngle / 2 else -jitterAngle / 2
            YawMode.BACKWARDS -> baseYaw + 180f
            YawMode.RANDOM -> Random.nextFloat() * 360f - 180f
            YawMode.OFF -> baseYaw
        }
        val pitch = when (pitchMode) {
            PitchMode.DOWN -> 90f
            PitchMode.UP -> -90f
            PitchMode.ZERO -> 0f
            PitchMode.JITTER -> if (flip) (jitterAngle / 2).coerceAtMost(90f) else -(jitterAngle / 2).coerceAtMost(90f)
            PitchMode.RANDOM -> Random.nextFloat() * 180f - 90f
            PitchMode.OFF -> player.xRot
        }
        // Instant turns (180°/tick): the whole point is to look unnatural.
        RotationManager.aim(this, Rotation(yaw, pitch), RotationManager.Priority.FUN)
    }
}
