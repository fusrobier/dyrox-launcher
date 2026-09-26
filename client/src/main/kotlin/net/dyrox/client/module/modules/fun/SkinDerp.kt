package net.dyrox.client.module.modules.`fun`

import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc
import net.minecraft.world.entity.player.PlayerModelPart
import kotlin.random.Random

/** Flickers your skin layers (hat, jacket, sleeves, trousers, cape) for everyone to see. */
object SkinDerp : Module("SkinDerp", Category.FUN, "Randomly toggles your skin layers.") {
    private val interval by int("Interval", 4, 1..40, description = "Ticks between changes")

    private var saved: Set<PlayerModelPart> = emptySet()
    private var ticks = 0

    override fun onEnable() {
        saved = PlayerModelPart.entries.filter { mc.options.isModelPartEnabled(it) }.toSet()
    }

    override fun onDisable() {
        PlayerModelPart.entries.forEach { mc.options.setModelPart(it, it in saved) }
        mc.options.broadcastOptions()
    }

    @Suppress("unused")
    private val onTick = handler<PlayerTickEvent> {
        if (++ticks < interval) return@handler
        ticks = 0
        PlayerModelPart.entries.forEach { mc.options.setModelPart(it, Random.nextBoolean()) }
        mc.options.broadcastOptions()
    }
}
