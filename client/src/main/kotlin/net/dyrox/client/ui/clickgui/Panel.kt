package net.dyrox.client.ui.clickgui

import net.dyrox.client.config.Configurable
import net.dyrox.client.module.Category
import net.dyrox.client.module.Module
import net.dyrox.client.module.TriggerModule
import net.dyrox.client.module.modules.render.ClickGui
import net.dyrox.client.render.Animated
import net.dyrox.client.render.Colors
import net.dyrox.client.render.Draw
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
    private var expanded = false
    private val open = Animated(0f, ClickGui.duration(200f))
    private val highlight = Animated(if (module.enabled) 1f else 0f, ClickGui.duration(160f))

    private val settingsHeight get() = settings.filter { it.visible }.sumOf { it.height } + if (settings.isEmpty()) 0 else 3

    override val height: Int get() = Style.MODULE_HEIGHT + (settingsHeight * open.value).roundToInt()

    override fun tooltip(): String = module.description

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        open.animateTo(if (expanded) 1f else 0f)
        highlight.animateTo(if (module.enabled) 1f else 0f)
        val rowHovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + Style.MODULE_HEIGHT
        val on = highlight.value

        if (rowHovered) Draw.rect(g, x, y, width, Style.MODULE_HEIGHT, Style.HOVER)
        if (on > 0.01f) Draw.rect(g, x, y, width, Style.MODULE_HEIGHT, Colors.fade(Colors.withAlpha(Style.accent, 38), on))
        Draw.rect(g, x, y + 3, 2, Style.MODULE_HEIGHT - 6, Colors.fade(Style.accent, on))
        val nameColor = Colors.lerp(if (rowHovered) Style.TEXT else Style.TEXT_DIM, Style.accent, on)
        Draw.text(g, module.name, x + Style.PADDING + 2, y + 4, nameColor)
        if (settings.isNotEmpty()) {
            Draw.text(g, if (expanded) "−" else "+", x + width - Style.PADDING - 5, y + 4, Style.TEXT_MUTED)
        }

        if (open.value <= 0.01f) return
        val bottom = y + height
        g.enableScissor(x, y + Style.MODULE_HEIGHT, x + width, bottom)
        Draw.rect(g, x, y + Style.MODULE_HEIGHT, width, bottom - y - Style.MODULE_HEIGHT, Colors.withAlpha(0x000000, 60))
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

    /** Body height on screen, capped so tall panels scroll instead of running off-screen. */
    private fun viewportHeight(screenHeight: Int): Int =
        (contentHeight() * expand.value).roundToInt().coerceAtMost((screenHeight - y - Style.HEADER_HEIGHT - 8).coerceAtLeast(40))

    fun totalHeight(screenHeight: Int) = Style.HEADER_HEIGHT + viewportHeight(screenHeight) + 4

    fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screenHeight: Int) {
        expand.animateTo(if (state.expanded.value) 1f else 0f)
        val viewport = viewportHeight(screenHeight)
        scrollTarget = scrollTarget.coerceIn(0f, (contentHeight() - viewport).coerceAtLeast(0).toFloat())
        scroll.animateTo(scrollTarget)

        val total = Style.HEADER_HEIGHT + viewport + 4
        Draw.roundedOutlined(g, x, y, width, total, Style.RADIUS, Colors.withAlpha(Style.PANEL, 245), Style.BORDER)
        Draw.roundedRect(g, x + 1, y + 1, width - 2, Style.HEADER_HEIGHT - 1, Style.RADIUS - 1, Style.HEADER)
        Draw.rect(g, x + 1, y + Style.HEADER_HEIGHT - 4, width - 2, 4, Style.HEADER) // square off the header's bottom corners
        Draw.rect(g, x + 8, y + Style.HEADER_HEIGHT - 1, width - 16, 1, Colors.withAlpha(Style.accent, 160))
        Draw.text(g, category.displayName, x + 8, y + 6, Style.TEXT)
        val count = buttons.count { it.module.enabled }
        if (count > 0) Draw.text(g, count.toString(), x + width - 8 - Draw.width(count.toString()), y + 6, Style.accent)

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
            Draw.roundedRect(g, x + width - 4, barY, 2, barHeight, 1, Colors.withAlpha(Style.TEXT_DIM, 120))
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
