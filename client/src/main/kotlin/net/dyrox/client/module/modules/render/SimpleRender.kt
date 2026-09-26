package net.dyrox.client.module.modules.render

import net.dyrox.client.config.BindMode
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.render.Animated
import net.dyrox.client.render.Easing

/** Full brightness everywhere (night vision on the lightmap, no darkness effect). */
object Fullbright : Module("Fullbright", Category.RENDER, "See in the dark.")

/** No screen shake when you take damage. */
object NoHurtCam : Module("NoHurtCam", Category.RENDER, "Removes the hurt camera shake.")

/** Hold C to zoom like OptiFine, with smooth animation and slower mouse turning. */
object Zoom : Module("Zoom", Category.RENDER, "Zooms in while held.", defaultKey = "key.keyboard.c", hidden = true, defaultBindMode = BindMode.HOLD) {
    private val factor by float("Zoom", 4f, 1.5f..15f, 0.5f)
    private val smooth by boolean("Smooth", true)
    private val slowMouse by boolean("Slow mouse", true, "Turn slower while zoomed")

    private val zoom = Animated(1f, 180f, Easing::outCubic)

    init {
        enabledValue.onChange { on -> zoom.animateTo(if (on) factor else 1f) }
    }

    private fun current(): Float {
        zoom.durationMillis = if (smooth) 180f else 0f
        if (enabled && zoom.target != factor) zoom.animateTo(factor)
        return zoom.value
    }

    fun modifyFov(fov: Float): Float {
        val z = current()
        return if (z <= 1.001f) fov else fov / z
    }

    fun mouseScale(): Double {
        val z = current()
        return if (!slowMouse || z <= 1.001f) 1.0 else 1.0 / z
    }
}

/** Third-person camera passes through walls; optional custom distance. */
object CameraClip : Module("CameraClip", Category.RENDER, "Third-person camera ignores walls.") {
    private val distance by float("Distance", 4f, 1f..20f, 0.5f)

    fun modifyDistance(vanilla: Float): Float = if (enabled) vanilla * (distance / 4f) else vanilla
}
