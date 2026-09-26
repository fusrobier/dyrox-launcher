package net.dyrox.client.module.modules.world

import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.util.format

/**
 * Runs the client's game ticks faster or slower (movement, attacks, animations). Servers limit how
 * fast you can move, so high values get you set back on most servers.
 */
object Timer : Module("Timer", Category.WORLD, "Changes the client tick speed.") {
    val speed by float("Speed", 1.5f, 0.1f..5f, 0.05f, "1 = normal (20 ticks/s)")

    override val tag: String get() = speed.format(2) + "x"
}
