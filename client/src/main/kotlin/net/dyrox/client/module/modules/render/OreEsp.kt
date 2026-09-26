package net.dyrox.client.module.modules.render

import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.WorldChangeEvent
import net.dyrox.client.event.WorldGizmoEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.render.WorldShapes
import net.dyrox.client.util.mc
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.tags.BlockItemTags
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

/**
 * X-ray for ores: scans the chunks around you (a few per tick) and outlines the selected ores
 * through walls. Only shows what the server sent you, so anti-xray plugins still hide ores.
 */
object OreEsp : Module("OreESP", Category.RENDER, "Outlines ores through walls.") {
    enum class Ore(override val choiceName: String, val color: Int, val matches: (BlockState) -> Boolean) : NamedChoice {
        DIAMOND("Diamond", 0xFF4AEDD9.toInt(), { it.`is`(BlockItemTags.DIAMOND_ORES.block()) }),
        ANCIENT_DEBRIS("Ancient debris", 0xFFA0522D.toInt(), { it.block == Blocks.ANCIENT_DEBRIS }),
        EMERALD("Emerald", 0xFF2EE66B.toInt(), { it.`is`(BlockItemTags.EMERALD_ORES.block()) }),
        GOLD("Gold", 0xFFFFD83D.toInt(), { it.`is`(BlockItemTags.GOLD_ORES.block()) }),
        IRON("Iron", 0xFFD8AF93.toInt(), { it.`is`(BlockItemTags.IRON_ORES.block()) }),
        REDSTONE("Redstone", 0xFFFF3030.toInt(), { it.`is`(BlockItemTags.REDSTONE_ORES.block()) }),
        LAPIS("Lapis", 0xFF3060FF.toInt(), { it.`is`(BlockItemTags.LAPIS_ORES.block()) }),
        COPPER("Copper", 0xFFE0773C.toInt(), { it.`is`(BlockItemTags.COPPER_ORES.block()) }),
        COAL("Coal", 0xFF505050.toInt(), { it.`is`(BlockItemTags.COAL_ORES.block()) }),
    }

    private val oresSetting = multiChoice("Ores", setOf(Ore.DIAMOND, Ore.ANCIENT_DEBRIS, Ore.EMERALD, Ore.GOLD), Ore.entries)
    private val ores: Set<Ore> get() = oresSetting.value
    private val radius by int("Radius", 3, 1..8, description = "Chunks around you")
    private val chunksPerTick by int("Chunks per tick", 2, 1..16, description = "Higher rescans faster but costs more frame time")
    private val fill by int("Box fill", 50, 0..255)
    private val maxBoxes by int("Max boxes", 1500, 100..10000, 100, "Only the nearest ores are drawn; keeps the frame rate up with common ores selected")

    private class Found(val box: AABB, val color: Int)

    private val found = HashMap<Long, List<Found>>()
    private var scanQueue = ArrayDeque<ChunkPos>()

    /** The nearest [maxBoxes] ores, re-sorted a few times a second (sorting every frame would cost more than drawing). */
    private var visible: List<Found> = emptyList()
    private var ticks = 0

    init {
        // Different ores selected: start over.
        oresSetting.onChange {
            found.clear()
            scanQueue.clear()
        }
    }

    override val tag: String get() = found.values.sumOf { it.size }.toString()

    override fun onDisable() {
        found.clear()
        scanQueue.clear()
        visible = emptyList()
    }

    @Suppress("unused")
    private val onWorld = handler<WorldChangeEvent> {
        found.clear()
        scanQueue.clear()
        visible = emptyList()
    }

    @Suppress("unused")
    private val onTick = handler<PlayerTickEvent> {
        val level = mc.level ?: return@handler
        val player = mc.player ?: return@handler
        val centerX = SectionPos.blockToSectionCoord(player.blockX)
        val centerZ = SectionPos.blockToSectionCoord(player.blockZ)
        // Forget chunks that are out of range.
        found.keys.removeIf { key ->
            val pos = ChunkPos.unpack(key)
            maxOf(kotlin.math.abs(pos.x - centerX), kotlin.math.abs(pos.z - centerZ)) > radius
        }
        if (scanQueue.isEmpty()) {
            // Nearest chunks first, then around again: blocks you mine disappear on the next pass.
            scanQueue = ArrayDeque(
                (centerX - radius..centerX + radius).flatMap { x -> (centerZ - radius..centerZ + radius).map { z -> ChunkPos(x, z) } }
                    .sortedBy { maxOf(kotlin.math.abs(it.x - centerX), kotlin.math.abs(it.z - centerZ)) },
            )
        }
        val selected = ores.toList()
        repeat(chunksPerTick) {
            val pos = scanQueue.removeFirstOrNull() ?: return@repeat
            if (!level.hasChunk(pos.x, pos.z)) return@repeat
            found[pos.pack()] = scanChunk(pos, selected)
        }
        if (ticks++ % 5 == 0) {
            val eye = player.eyePosition
            visible = found.values.flatten().sortedBy { it.box.center.distanceToSqr(eye) }.take(maxBoxes)
        }
    }

    @Suppress("unused")
    private val onGizmos = handler<WorldGizmoEvent> {
        for (entry in visible) WorldShapes.box(entry.box, entry.color, fill)
    }

    private fun scanChunk(pos: ChunkPos, selected: List<Ore>): List<Found> {
        val level = mc.level ?: return emptyList()
        val chunk = level.getChunk(pos.x, pos.z)
        val result = ArrayList<Found>()
        val sections = chunk.sections
        for (index in sections.indices) {
            val section = sections[index]
            if (section.hasOnlyAir() || !section.maybeHas { state -> selected.any { it.matches(state) } }) continue
            val baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index))
            for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                val state = section.getBlockState(x, y, z)
                val ore = selected.firstOrNull { it.matches(state) } ?: continue
                val block = BlockPos(pos.minBlockX + x, baseY + y, pos.minBlockZ + z)
                result += Found(AABB(block).deflate(0.02), ore.color)
            }
        }
        return result
    }
}
