package net.dyrox.client.module.modules.movement

import net.dyrox.client.module.Category
import net.dyrox.client.module.Module

/**
 * Walk at (up to) full speed while eating, drinking, blocking or drawing a bow, and optionally keep
 * sprinting. Read by the LocalPlayer mixin (itemUseSpeedMultiplier / isSlowDueToUsingItem).
 */
object NoSlow : Module("NoSlow", Category.MOVEMENT, "No slowdown while using items.") {
    val speed by float("Speed", 1f, 0.2f..1f, 0.05f, "Movement while using an item (vanilla: 0.2)")
    val sprint by boolean("Sprint", true, "Keep sprinting while using an item")
}
