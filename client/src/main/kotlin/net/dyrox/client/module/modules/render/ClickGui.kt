package net.dyrox.client.module.modules.render

import net.dyrox.client.config.DyroxColor
import net.dyrox.client.module.Category
import net.dyrox.client.module.TriggerModule
import net.dyrox.client.render.Colors
import net.dyrox.client.ui.clickgui.ClickGuiScreen
import net.dyrox.shared.theme.DyroxPalette
import net.minecraft.client.Minecraft

/** Opens the ClickGUI (Right Shift by default) and holds the theme settings shared by all Dyrox UI. */
object ClickGui : TriggerModule("ClickGUI", Category.RENDER, "Opens the module menu.", defaultKey = "key.keyboard.right.shift") {
    val accentColor by color("Accent", DyroxColor(DyroxPalette.ACCENT), "Highlight colour of the ClickGUI and HUD")
    val blur by boolean("Blur", true, "Blur the game behind the menu")
    val animationSpeed by float("Animation speed", 1f, 0.25f..3f, 0.05f, "1 = default, higher = faster")

    /** The accent colour right now (cycles when "rainbow" is on). */
    val accent: Int get() = accentColor.let { if (it.rainbow) Colors.rainbow() else it.argb }

    fun duration(baseMillis: Float): Float = baseMillis / animationSpeed

    override fun trigger() {
        val minecraft = Minecraft.getInstance()
        // Deferred: when triggered from chat (".t clickgui"), chat closes itself right after this call.
        minecraft.execute {
            if (minecraft.gui.screen() !is ClickGuiScreen) minecraft.gui.setScreen(ClickGuiScreen())
        }
    }
}
