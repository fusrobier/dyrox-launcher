package net.dyrox.client.render

import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft

/**
 * Smooth, temporary third-person view (used by AntiAim so you can watch your own spoofed rotations).
 *
 * [enter] switches to third person and glides the camera out from the head to the requested distance;
 * [exit] glides it back in and then restores the previous perspective. Pressing F5 in between hands
 * control back to the player: the old perspective is then not restored.
 */
object CameraController {
    /** Where the glide starts/ends: close enough to feel like first person, far enough not to clip the head. */
    private const val NEAR = 0.6f

    private val distance = Animated(NEAR, 450f, Easing::inOutCubic)
    private var owner: Any? = null
    private var restoreTo: CameraType? = null
    private var appliedType: CameraType? = null
    private var exiting = false

    val isActive: Boolean get() = appliedType != null

    fun enter(owner: Any, targetDistance: Float, durationMillis: Float) {
        val options = Minecraft.getInstance().options
        this.owner = owner
        exiting = false
        distance.durationMillis = durationMillis
        if (appliedType == null) {
            val current = options.cameraType
            if (current.isFirstPerson) {
                restoreTo = current
                appliedType = CameraType.THIRD_PERSON_BACK
                options.cameraType = CameraType.THIRD_PERSON_BACK
                distance.snapTo(NEAR)
            } else {
                // Already in third person: just take over the distance, never switch perspective back.
                restoreTo = null
                appliedType = current
                distance.snapTo(4f)
            }
        }
        distance.animateTo(targetDistance)
    }

    /** Target distance changed in the settings while active. */
    fun retarget(targetDistance: Float) {
        if (isActive && !exiting) distance.animateTo(targetDistance)
    }

    fun exit(owner: Any) {
        if (this.owner !== owner || appliedType == null) return
        exiting = true
        distance.animateTo(if (restoreTo != null) NEAR else 4f)
    }

    /** Every client tick: finish an exit once the camera is back at the head; detect F5 presses. */
    fun tick() {
        val applied = appliedType ?: return
        val options = Minecraft.getInstance().options
        if (options.cameraType != applied) {
            // The player changed perspective themselves: stop managing it.
            clear()
            return
        }
        if (exiting && distance.isDone) {
            restoreTo?.let { options.cameraType = it }
            clear()
        }
    }

    /** Camera distance for this frame; [vanilla] is what the game would use (4 blocks × scale). */
    fun modifyDistance(vanilla: Float): Float = if (appliedType != null) distance.value * (vanilla / 4f) else vanilla

    private fun clear() {
        owner = null
        appliedType = null
        restoreTo = null
        exiting = false
    }
}
