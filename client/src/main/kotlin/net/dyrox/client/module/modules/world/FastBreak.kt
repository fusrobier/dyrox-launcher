package net.dyrox.client.module.modules.world

import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.mixin.MultiPlayerGameModeAccessor
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.mc

/**
 * Mines faster. Vanilla servers accept a block once 70 % of it is broken ("Break at"), and there is
 * a 5-tick pause between blocks that can be skipped.
 */
object FastBreak : Module("FastBreak", Category.WORLD, "Breaks blocks faster.") {
    private val breakAt by float("Break at", 0.7f, 0.7f..1f, 0.05f, "Finish the block at this progress (vanilla server minimum: 0.7)")
    private val noDelay by boolean("No delay", true, "Skip the 5-tick pause between blocks")

    @Suppress("unused")
    private val onTick = handler<PlayerTickEvent> {
        val gameMode = mc.gameMode ?: return@handler
        val accessor = gameMode as MultiPlayerGameModeAccessor
        if (noDelay && accessor.`dyrox$getDestroyDelay`() > 0) accessor.`dyrox$setDestroyDelay`(0)
        if (gameMode.isDestroying && accessor.`dyrox$getDestroyProgress`() >= breakAt) accessor.`dyrox$setDestroyProgress`(1f)
    }
}
