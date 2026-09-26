package net.dyrox.client.module.modules.render

import net.dyrox.client.DyroxClient
import net.dyrox.client.config.NamedChoice
import net.dyrox.client.event.Render2DEvent
import net.dyrox.client.event.handler
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.render.Animated
import net.dyrox.client.render.Colors
import net.dyrox.client.render.Draw
import net.dyrox.client.render.Easing
import net.dyrox.client.ui.hud.NotificationType
import net.dyrox.client.ui.hud.Notifications
import net.dyrox.shared.theme.DyroxPalette
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Watermark, array list of enabled modules, and notifications. */
object Hud : Module("HUD", Category.RENDER, "Shows the watermark, active modules and notifications.", defaultEnabled = true, hidden = true) {
    enum class ColorMode(override val choiceName: String) : NamedChoice { ACCENT("Accent"), RAINBOW("Rainbow") }

    val watermark by boolean("Watermark", true)
    val arrayList by boolean("Array list", true, "List of enabled modules, top right")
    val colorMode by choice("Colors", ColorMode.ACCENT)
    val background by boolean("Background", true, "Dark backing behind array list entries")
    val notifications by boolean("Notifications", true)
    val toggleNotifications by boolean("Toggle notifications", true, "Notify when a module is enabled or disabled")

    /** Per-module slide animation: 0 = hidden, 1 = fully shown. Kept for disabled modules while they slide out. */
    private val slides = HashMap<Module, Animated>()

    init {
        Module.toggleListeners += { module, enabled ->
            if (this.enabled && notifications && toggleNotifications && !module.hidden) {
                Notifications.show(module.name, if (enabled) "Enabled" else "Disabled", if (enabled) NotificationType.SUCCESS else NotificationType.INFO, 1_500)
            }
        }
    }

    @Suppress("unused")
    private val onRender = handler<Render2DEvent> { event ->
        val minecraft = Minecraft.getInstance()
        // Respect F1 (hidden GUI) and stay out of the way of the debug screen.
        if (minecraft.gui.hud.isHidden) return@handler
        val g = event.graphics
        if (watermark) renderWatermark(g, minecraft)
        if (arrayList) renderArrayList(g)
        if (notifications) Notifications.render(g)
    }

    private fun lineColor(index: Int): Int = when (colorMode) {
        ColorMode.ACCENT -> ClickGui.accent
        ColorMode.RAINBOW -> Colors.rainbow(offsetMillis = -index * 150L)
    }

    private fun renderWatermark(g: GuiGraphicsExtractor, minecraft: Minecraft) {
        val brand = "Dyrox"
        val info = " ${DyroxClient.version} · ${minecraft.fps} FPS"
        val width = Draw.width(brand) + Draw.width(info) + 12
        Draw.roundedRect(g, 4, 4, width, 15, 4, Colors.withAlpha(DyroxPalette.BACKGROUND, 190))
        Draw.roundedRect(g, 4, 4, 2, 15, 1, lineColor(0))
        Draw.text(g, brand, 10, 8, lineColor(0), shadow = true)
        Draw.text(g, info, 10 + Draw.width(brand), 8, DyroxPalette.TEXT_SECONDARY, shadow = true)
    }

    private fun renderArrayList(g: GuiGraphicsExtractor) {
        val modules = DyroxClient.modules.modules.filter { !it.hidden }
        for (module in modules) {
            val slide = slides.getOrPut(module) { Animated(if (module.enabled) 1f else 0f, 220f, Easing::outCubic) }
            slide.animateTo(if (module.enabled) 1f else 0f)
        }
        val shown = modules
            .filter { (slides[it]?.value ?: 0f) > 0.01f }
            .map { it to label(it) }
            .sortedByDescending { Draw.width(it.second) }
        val right = g.guiWidth() - 4
        var y = 4f
        shown.forEachIndexed { index, (module, text) ->
            val progress = slides.getValue(module).value
            val lineHeight = 11
            val textWidth = Draw.width(text)
            val x = right - ((textWidth + 6) * progress).toInt()
            val top = y.toInt()
            if (background) Draw.rect(g, x - 3, top, textWidth + 6, lineHeight, Colors.withAlpha(DyroxPalette.BACKGROUND, (160 * progress).toInt()))
            Draw.rect(g, right, top, 2, lineHeight, Colors.fade(lineColor(index), progress))
            Draw.text(g, text, x, top + 2, Colors.fade(lineColor(index), progress), shadow = true)
            y += lineHeight * progress
        }
    }

    private fun label(module: Module): String = module.tag?.let { "${module.name} §7$it" } ?: module.name
}
