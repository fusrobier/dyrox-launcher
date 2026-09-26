package net.dyrox.client.ui.clickgui

import net.dyrox.client.config.Configurable
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.module.TriggerModule
import net.dyrox.client.module.modules.render.ClickGui
import net.dyrox.client.render.Animated
import net.dyrox.client.render.Colors
import net.dyrox.client.render.Draw
import net.dyrox.client.render.Glass
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Saved panel layout (config section `gui`). x/y of -1 mean "not placed yet". */
class PanelState(category: Category) : Configurable(category.displayName) {
    val x = int("X", -1, -1..10_000)
    val y = int("Y", -1, -1..10_000)
    val expanded = boolean("Expanded", true)
}

/** One module row: left click toggles, right click expands its settings. */
class ModuleButton(val module: Module) : Element() {
    val settings: List<Element> = SettingElements.forValues(module.values)
    private var expanded = module.name.lowercase() in net.dyrox.client.DebugHooks.expandedModules
    private val open = Animated(if (expanded) 1f else 0f, ClickGui.duration(200f))
    private val highlight = Animated(if (module.enabled) 1f else 0f, ClickGui.duration(160f))

    private val settingsHeight get() = settings.filter { it.visible }.sumOf { it.height } + if (settings.isEmpty()) 0 else 3

    override val height: Int get() = Style.MODULE_HEIGHT + (settingsHeight * open.value).roundToInt()

    override fun tooltip(): String = module.description

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        open.animateTo(if (expanded) 1f else 0f)
        highlight.animateTo(if (module.enabled) 1f else 0f)
        val rowHovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + Style.MODULE_HEIGHT
        val on = highlight.value

        // Capsule rows: a faint glass capsule on hover, an accent-tinted one when enabled.
        val capsuleX = x + 4
        val capsuleWidth = width - 8
        val capsuleY = y + 1
        val capsuleHeight = Style.MODULE_HEIGHT - 2
        if (rowHovered) Draw.roundedRect(g, capsuleX, capsuleY, capsuleWidth, capsuleHeight, Style.ROW_RADIUS, Glass.HOVER)
        if (on > 0.01f) {
            Draw.roundedRect(g, capsuleX, capsuleY, capsuleWidth, capsuleHeight, Style.ROW_RADIUS, Colors.fade(Colors.withAlpha(Style.accent, 72), on))
            Draw.roundedRing(g, capsuleX, capsuleY, capsuleWidth, capsuleHeight, Style.ROW_RADIUS, Colors.fade(Colors.withAlpha(Style.accent, 140), on))
        }
        val nameColor = Colors.lerp(if (rowHovered) Glass.TEXT else Glass.TEXT_DIM, Glass.TEXT, on)
        Draw.text(g, module.name, x + Style.PADDING + 3, Style.textY(y, Style.MODULE_HEIGHT), nameColor, bold = on > 0.5f)
        if (settings.isNotEmpty()) {
            // Chevron-like indicator: rotates from "›" to "⌄" feel via two glyphs.
            Draw.text(g, if (expanded) "–" else "···", x + width - Style.PADDING - Draw.width(if (expanded) "–" else "···") - 2, Style.textY(y, Style.MODULE_HEIGHT), Glass.TEXT_MUTED)
        }

        if (open.value <= 0.01f) return
        val bottom = y + height
        g.enableScissor(x, y + Style.MODULE_HEIGHT, x + width, bottom)
        Draw.roundedRect(g, x + 4, y + Style.MODULE_HEIGHT, width - 8, bottom - y - Style.MODULE_HEIGHT - 1, Style.ROW_RADIUS, 0x1A000000)
        var childY = y + Style.MODULE_HEIGHT + 1
        for (child in settings.filter { it.visible }) {
            child.x = x + 2
            child.y = childY
            child.width = width - 4
            child.render(g, mouseX, mouseY)
            childY += child.height
        }
        g.disableScissor()
    }

    fun hoveredElement(mouseX: Int, mouseY: Int): Element? {
        if (mouseY < y + Style.MODULE_HEIGHT) return this
        if (open.value < 0.99f) return null
        return settings.filter { it.visible }.firstOrNull { it.isHovered(mouseX, mouseY) }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseY < y + Style.MODULE_HEIGHT) {
            when (button) {
                Buttons.LEFT -> if (module is TriggerModule) module.trigger() else module.toggle()
                Buttons.RIGHT -> if (settings.isNotEmpty()) expanded = !expanded
                else -> return false
            }
            return true
        }
        if (!expanded) return false
        return settings.filter { it.visible }.firstOrNull { it.isHovered(mouseX, mouseY) }?.mouseClicked(mouseX, mouseY, button) ?: false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int) = settings.forEach { it.mouseDragged(mouseX, mouseY, button) }
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) = settings.forEach { it.mouseReleased(mouseX, mouseY, button) }
}

/** A category column: draggable header, collapsible, scrollable body. */
class Panel(val category: Category, val state: PanelState, modules: List<Module>) {
    val buttons = modules.map(::ModuleButton)
    private val expand = Animated(if (state.expanded.value) 1f else 0f, ClickGui.duration(220f))
    private val scroll = Animated(0f, ClickGui.duration(120f))
    private var scrollTarget = 0f
    private var drag: Pair<Double, Double>? = null
    var filter: (Module) -> Boolean = { true }

    val x get() = state.x.value
    val y get() = state.y.value
    val width = Style.PANEL_WIDTH

    val visibleButtons: List<ModuleButton> get() = buttons.filter { filter(it.module) }

    private fun contentHeight() = visibleButtons.sumOf { it.height }

    /** Y where this panel must end: the top of the panel below it (set by the screen every frame). */
    var bottomLimit: Int = Int.MAX_VALUE

    /** Body height on screen, capped so tall panels scroll instead of running off-screen or over the panel below. */
    private fun viewportHeight(screenHeight: Int): Int {
        val bottom = minOf(screenHeight - 8, bottomLimit)
        return (contentHeight() * expand.value).roundToInt().coerceAtMost((bottom - y - Style.HEADER_HEIGHT - 4).coerceAtLeast(Style.MODULE_HEIGHT))
    }

    fun totalHeight(screenHeight: Int) = Style.HEADER_HEIGHT + viewportHeight(screenHeight) + 4

    fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screenHeight: Int) {
        expand.animateTo(if (state.expanded.value) 1f else 0f)
        val viewport = viewportHeight(screenHeight)
        scrollTarget = scrollTarget.coerceIn(0f, (contentHeight() - viewport).coerceAtLeast(0).toFloat())
        scroll.animateTo(scrollTarget)

        val total = Style.HEADER_HEIGHT + viewport + 4
        Draw.glass(g, x, y, width, total, Style.RADIUS)
        val headerTextY = Style.textY(y, Style.HEADER_HEIGHT)
        Draw.text(g, category.displayName, x + Style.PADDING + 3, headerTextY, Glass.TEXT, bold = true)
        val count = buttons.count { it.module.enabled }
        if (count > 0) {
            // Small accent "badge" with the number of enabled modules.
            val label = count.toString()
            val badgeWidth = Draw.width(label) + 8
            val badgeX = x + width - Style.PADDING - badgeWidth
            Draw.roundedRect(g, badgeX, y + 6, badgeWidth, 12, 6, Style.accent)
            Draw.centeredText(g, label, badgeX + badgeWidth / 2, Style.textY(y + 6, 12), 0xFF0B0D10.toInt())
        }
        if (viewport > 0) Draw.rect(g, x + 10, y + Style.HEADER_HEIGHT - 1, width - 20, 1, Glass.DIVIDER)

        if (viewport <= 0) return
        val top = y + Style.HEADER_HEIGHT
        g.enableScissor(x + 1, top, x + width - 1, top + viewport)
        var rowY = top - scroll.value.roundToInt()
        for (button in visibleButtons) {
            button.x = x + 1
            button.y = rowY
            button.width = width - 2
            if (rowY + button.height >= top && rowY <= top + viewport) button.render(g, mouseX, mouseY)
            rowY += button.height
        }
        g.disableScissor()
        // Scrollbar when the content doesn't fit.
        val content = contentHeight()
        if (content > viewport) {
            val barHeight = (viewport * viewport / content.toFloat()).roundToInt().coerceAtLeast(10)
            val barY = top + ((viewport - barHeight) * (scroll.value / (content - viewport))).roundToInt()
            Draw.roundedRect(g, x + width - 4, barY, 2, barHeight, 1, Glass.TEXT_MUTED)
        }
    }

    fun isOver(mouseX: Double, mouseY: Double, screenHeight: Int) =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + totalHeight(screenHeight)

    private fun isOverHeader(mouseX: Double, mouseY: Double) = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + Style.HEADER_HEIGHT

    private fun buttonAt(mouseX: Double, mouseY: Double, screenHeight: Int): ModuleButton? {
        val top = y + Style.HEADER_HEIGHT
        if (mouseY < top || mouseY >= top + viewportHeight(screenHeight)) return null
        return visibleButtons.firstOrNull { it.isHovered(mouseX, mouseY) }
    }

    fun hoveredElement(mouseX: Int, mouseY: Int, screenHeight: Int): Element? =
        buttonAt(mouseX.toDouble(), mouseY.toDouble(), screenHeight)?.hoveredElement(mouseX, mouseY)

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, screenHeight: Int): Boolean {
        if (isOverHeader(mouseX, mouseY)) {
            when (button) {
                Buttons.LEFT -> drag = (mouseX - x) to (mouseY - y)
                Buttons.RIGHT -> state.expanded.value = !state.expanded.value
            }
            return true
        }
        return buttonAt(mouseX, mouseY, screenHeight)?.mouseClicked(mouseX, mouseY, button) ?: isOver(mouseX, mouseY, screenHeight)
    }

    fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, screenWidth: Int, screenHeight: Int) {
        drag?.let { (dx, dy) ->
            state.x.value = (mouseX - dx).roundToInt().coerceIn(0, (screenWidth - width).coerceAtLeast(0))
            state.y.value = (mouseY - dy).roundToInt().coerceIn(0, (screenHeight - Style.HEADER_HEIGHT).coerceAtLeast(0))
        }
        buttons.forEach { it.mouseDragged(mouseX, mouseY, button) }
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
        drag = null
        buttons.forEach { it.mouseReleased(mouseX, mouseY, button) }
    }

    fun scroll(amount: Double) {
        scrollTarget -= (amount * 24).toFloat()
    }
}
