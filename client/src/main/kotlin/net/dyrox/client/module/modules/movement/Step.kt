package net.dyrox.client.module.modules.movement

import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.format
import net.dyrox.client.util.mc
import net.minecraft.world.entity.ai.attributes.Attributes

/** Walk up full blocks (or higher) without jumping, by raising the step-height attribute on your side. */
object Step : Module("Step", Category.MOVEMENT, "Walk up blocks without jumping.") {
    private const val VANILLA_STEP = 0.6

    private val height by float("Height", 1f, 0.6f..2.5f, 0.1f)

    override val tag: String get() = height.format(1)

    @Suppress("unused")
    private val onTick = handler<PlayerTickEvent> {
        // Re-applied every tick: respawns and server attribute updates reset the base value.
        val attribute = mc.player?.getAttribute(Attributes.STEP_HEIGHT) ?: return@handler
        if (attribute.baseValue != height.toDouble()) attribute.baseValue = height.toDouble()
    }

    override fun onDisable() {
        mc.player?.getAttribute(Attributes.STEP_HEIGHT)?.baseValue = VANILLA_STEP
    }
}
