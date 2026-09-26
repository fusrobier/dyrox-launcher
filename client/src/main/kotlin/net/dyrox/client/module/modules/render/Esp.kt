package net.dyrox.client.module.modules.render

import net.dyrox.client.combat.TargetType
import net.dyrox.client.combat.Targets
import net.dyrox.client.config.DyroxColor
import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.WorldGizmoEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.render.WorldShapes
import net.dyrox.client.util.mc
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity

/**
 * Highlights entities through walls, either with Minecraft's own glowing outline or with boxes.
 * Friends get their own colour.
 */
object Esp : Module("ESP", Category.RENDER, "See entities through walls.") {
    enum class Mode(override val choiceName: String) : NamedChoice {
        GLOW("Glow"),
        BOX("Box"),
        BOTH("Both"),
    }

    enum class Kind(override val choiceName: String) : NamedChoice {
        PLAYERS("Players"),
        HOSTILE("Hostile"),
        PASSIVE("Passive"),
        ITEMS("Items"),
    }

    private val mode by choice("Mode", Mode.GLOW)
    private val kinds by multiChoice("Entities", setOf(Kind.PLAYERS, Kind.HOSTILE), Kind.entries)
    private val playerColor by color("Players", DyroxColor(0xFFFF5A5A.toInt()))
    private val friendColor by color("Friends", DyroxColor(0xFF5AC8FF.toInt()))
    private val hostileColor by color("Hostile", DyroxColor(0xFFFFB347.toInt()))
    private val passiveColor by color("Passive", DyroxColor(0xFF7CFF7C.toInt()))
    private val itemColor by color("Items", DyroxColor(0xFFE0E0E0.toInt()))
    private val fill by int("Box fill", 30, 0..255, description = "Opacity of the box faces").visibleIf { mode != Mode.GLOW }

    override val tag: String get() = mode.choiceName

    /** Colour for [entity], or null if ESP doesn't show it. */
    fun colorOf(entity: Entity): Int? {
        if (entity === mc.player || entity === mc.cameraEntity) return null
        if (entity is ItemEntity) return if (Kind.ITEMS in kinds) resolve(itemColor) else null
        if (!entity.isAlive) return null
        val kind = when (Targets.typeOf(entity)) {
            TargetType.PLAYERS -> Kind.PLAYERS
            TargetType.HOSTILE -> Kind.HOSTILE
            TargetType.PASSIVE -> Kind.PASSIVE
            null -> return null
        }
        if (kind !in kinds) return null
        return resolve(
            when {
                Targets.isFriend(entity) -> friendColor
                kind == Kind.PLAYERS -> playerColor
                kind == Kind.HOSTILE -> hostileColor
                else -> passiveColor
            },
        )
    }

    fun shouldGlow(entity: Entity): Boolean = enabled && mode != Mode.BOX && colorOf(entity) != null

    fun glowColor(entity: Entity): Int? = if (shouldGlow(entity)) colorOf(entity)?.let { it or 0xFF000000.toInt() } else null

    @Suppress("unused")
    private val onGizmos = handler<WorldGizmoEvent> { event ->
        if (mode == Mode.GLOW) return@handler
        val level = mc.level ?: return@handler
        for (entity in level.entitiesForRendering()) {
            val color = colorOf(entity) ?: continue
            WorldShapes.box(WorldShapes.interpolatedBox(entity, event.partialTicks), color, fill)
        }
    }

    private fun resolve(color: DyroxColor): Int = if (color.rainbow) net.dyrox.client.render.Colors.rainbow() else color.argb
}
