package net.dyrox.client.module.modules.render

import net.dyrox.client.combat.TargetType
import net.dyrox.client.combat.Targets
import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.WorldGizmoEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.render.Colors
import net.dyrox.client.render.WorldShapes
import net.dyrox.client.util.mc

/** Lines from your crosshair to entities, coloured by distance (red = close) or by ESP colour. */
object Tracers : Module("Tracers", Category.RENDER, "Draws lines to entities.") {
    enum class ColorMode(override val choiceName: String) : NamedChoice {
        DISTANCE("Distance"),
        ESP("ESP colors"),
    }

    private val targets by multiChoice("Targets", setOf(TargetType.PLAYERS), TargetType.entries)
    private val colorMode by choice("Color", ColorMode.DISTANCE)
    private val maxDistance by int("Max distance", 128, 16..512, 8)
    private val width by float("Width", 1.5f, 0.5f..4f, 0.25f)

    @Suppress("unused")
    private val onGizmos = handler<WorldGizmoEvent> { event ->
        val level = mc.level ?: return@handler
        val player = mc.player ?: return@handler
        val start = WorldShapes.crosshair(event.camera)
        for (entity in level.entitiesForRendering()) {
            if (entity === player || !entity.isAlive || Targets.typeOf(entity) !in targets) continue
            val distance = entity.distanceTo(player)
            if (distance > maxDistance) continue
            val color = when (colorMode) {
                ColorMode.DISTANCE -> if (Targets.isFriend(entity)) 0xFF5AC8FF.toInt()
                else Colors.lerp(0xFFFF4040.toInt(), 0xFF40FF60.toInt(), (distance / 64f).coerceIn(0f, 1f))
                ColorMode.ESP -> Esp.colorOf(entity) ?: 0xFFFFFFFF.toInt()
            }
            val box = WorldShapes.interpolatedBox(entity, event.partialTicks)
            WorldShapes.line(start, box.center, color, width)
        }
    }
}
