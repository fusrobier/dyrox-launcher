package net.dyrox.client.module.modules.combat

import net.dyrox.client.combat.TargetType
import net.dyrox.client.combat.Targets
import net.dyrox.client.event.PlayerTickEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.PlayerUtil
import net.dyrox.client.util.mc
import net.minecraft.world.phys.EntityHitResult

/** Attacks whatever entity is under your crosshair as soon as the weapon has recharged. */
object TriggerBot : Module("TriggerBot", Category.COMBAT, "Attacks the entity you are looking at.") {
    private val targets by multiChoice("Targets", setOf(TargetType.PLAYERS, TargetType.HOSTILE), TargetType.entries)
    private val cooldown by float("Cooldown", 0.95f, 0.5f..1f, 0.01f, "Attack strength needed (1 = full)")
    private val delay by int("Delay", 0, 0..10, description = "Extra ticks to wait after the crosshair lands on a target")

    private var aimedTicks = 0

    @Suppress("unused")
    private val onTick = handler<PlayerTickEvent> {
        val player = mc.player ?: return@handler
        val hit = mc.hitResult as? EntityHitResult
        val entity = hit?.entity
        if (mc.gui.screen() != null || entity == null || !Targets.isAttackable(entity, targets) || player.isUsingItem) {
            aimedTicks = 0
            return@handler
        }
        if (aimedTicks++ < delay) return@handler
        if (player.getAttackStrengthScale(0.5f) < cooldown) return@handler
        PlayerUtil.attack(player, entity)
    }
}
