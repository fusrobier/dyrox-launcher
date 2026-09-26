package net.dyrox.client.render

import net.minecraft.client.Camera
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * World-space shapes drawn through Minecraft's gizmo system (only valid inside WorldGizmoEvent).
 * Everything is drawn on top of the world so it shows through walls.
 */
object WorldShapes {
    /** [entity]'s hitbox where it is rendered this frame (interpolated between ticks). */
    fun interpolatedBox(entity: Entity, partialTicks: Float): AABB {
        val renderPos = entity.getPosition(partialTicks)
        return entity.boundingBox.move(renderPos.subtract(entity.position()))
    }

    fun box(box: AABB, argb: Int, fillAlpha: Int = 0, lineWidth: Float = 2f) {
        val style = if (fillAlpha > 0) GizmoStyle.strokeAndFill(argb, lineWidth, Colors.withAlpha(argb, fillAlpha)) else GizmoStyle.stroke(argb, lineWidth)
        Gizmos.cuboid(box, style).setAlwaysOnTop()
    }

    fun line(from: Vec3, to: Vec3, argb: Int, width: Float = 1.5f) {
        Gizmos.line(from, to, argb, width).setAlwaysOnTop()
    }

    /** A point just in front of the camera: where tracers start (the crosshair). */
    fun crosshair(camera: Camera): Vec3 {
        val forward = camera.forwardVector()
        return camera.position().add(forward.x() * 0.5, forward.y() * 0.5, forward.z() * 0.5)
    }
}
