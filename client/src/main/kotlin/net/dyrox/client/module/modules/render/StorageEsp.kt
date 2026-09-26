package net.dyrox.client.module.modules.render

import net.dyrox.client.config.DyroxColor
import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.WorldGizmoEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.render.WorldShapes
import net.dyrox.client.util.mc
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.EnderChestBlockEntity
import net.minecraft.world.level.block.entity.HopperBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
import net.minecraft.world.phys.AABB

/** Boxes around chests, barrels, shulker boxes and other storage in loaded chunks. */
object StorageEsp : Module("StorageESP", Category.RENDER, "Highlights chests and other storage.") {
    enum class Kind(override val choiceName: String) : NamedChoice {
        CHESTS("Chests"),
        ENDER_CHESTS("Ender chests"),
        BARRELS("Barrels"),
        SHULKERS("Shulker boxes"),
        HOPPERS("Hoppers"),
        FURNACES("Furnaces"),
    }

    private val kinds by multiChoice("Blocks", setOf(Kind.CHESTS, Kind.ENDER_CHESTS, Kind.BARRELS, Kind.SHULKERS), Kind.entries)
    private val chestColor by color("Chest color", DyroxColor(0xFFFFB347.toInt()))
    private val enderColor by color("Ender chest color", DyroxColor(0xFFB36BFF.toInt()))
    private val barrelColor by color("Barrel color", DyroxColor(0xFFC8A064.toInt()))
    private val shulkerColor by color("Shulker color", DyroxColor(0xFFFF6FD8.toInt()))
    private val otherColor by color("Other color", DyroxColor(0xFFA0A0A0.toInt()))
    private val fill by int("Box fill", 40, 0..255)

    private class Found(val box: AABB, val color: Int)

    @Volatile
    private var found: List<Found> = emptyList()
    private var ticks = 0

    override val tag: String get() = found.size.toString()

    override fun onDisable() {
        found = emptyList()
    }

    // Block entities change rarely; rescanning twice a second is plenty and keeps frames cheap.
    @Suppress("unused")
    private val onTick = handler<PlayerTickEvent> {
        if (ticks++ % 10 != 0) return@handler
        val level = mc.level ?: return@handler
        val player = mc.player ?: return@handler
        val radius = mc.options.renderDistance().get()
        val centerX = SectionPos.blockToSectionCoord(player.blockX)
        val centerZ = SectionPos.blockToSectionCoord(player.blockZ)
        val result = ArrayList<Found>()
        for (cx in centerX - radius..centerX + radius) {
            for (cz in centerZ - radius..centerZ + radius) {
                if (!level.hasChunk(cx, cz)) continue
                for (blockEntity in level.getChunk(cx, cz).blockEntities.values) {
                    val color = colorOf(blockEntity) ?: continue
                    result += Found(boxOf(blockEntity.blockPos), color)
                }
            }
        }
        found = result
    }

    @Suppress("unused")
    private val onGizmos = handler<WorldGizmoEvent> {
        for (entry in found) WorldShapes.box(entry.box, entry.color, fill)
    }

    private fun colorOf(blockEntity: BlockEntity): Int? {
        val (kind, color) = when (blockEntity) {
            is ChestBlockEntity -> Kind.CHESTS to chestColor
            is EnderChestBlockEntity -> Kind.ENDER_CHESTS to enderColor
            is BarrelBlockEntity -> Kind.BARRELS to barrelColor
            is ShulkerBoxBlockEntity -> Kind.SHULKERS to shulkerColor
            is HopperBlockEntity -> Kind.HOPPERS to otherColor
            is AbstractFurnaceBlockEntity -> Kind.FURNACES to otherColor
            else -> return null
        }
        return if (kind in kinds) color.argb else null
    }

    private fun boxOf(pos: BlockPos): AABB = AABB(pos).deflate(0.0625)
}
