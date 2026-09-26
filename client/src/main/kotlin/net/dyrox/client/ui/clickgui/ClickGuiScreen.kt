package net.dyrox.client.ui.clickgui

import com.mojang.blaze3d.platform.InputConstants
import net.dyrox.client.DebugHooks
import net.dyrox.client.DyroxClient
import net.dyrox.client.module.Category
import net.dyrox.client.module.modules.render.ClickGui
import net.dyrox.client.render.Animated
import net.dyrox.client.render.Colors
import net.dyrox.client.render.Draw
import net.dyrox.client.render.Easing
import net.dyrox.client.render.Glass
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/** Panel layout for all categories; saved in the profile's `gui` section. */
object ClickGuiLayout {
    val states: Map<Category, PanelState> = Category.entries.associateWith { PanelState(it) }
}

/**
 * The module menu: one panel per category, a search bar, drag-to-move panels, right-click to expand.
 * Everything is drawn with [Draw] on vanilla GUI pipelines; the backdrop uses vanilla's menu blur.
 */
class ClickGuiScreen : Screen(Component.literal("Dyrox")) {
    private val panels: MutableList<Panel> = Category.entries
        .map { Panel(it, ClickGuiLayout.states.getValue(it), DyroxClient.modules.byCategory(it)) }
        .toMutableList()
    private val opening = Animated(0f, ClickGui.duration(200f), Easing::outBack)
    private var search = ""
    private var searchFocused = false

    /** True while a text field or key binding captures the keyboard (InventoryMove stays off then). */
    val isTyping: Boolean get() = searchFocused || Focus.element != null

    override fun init() {
        opening.snapTo(0f)
        opening.animateTo(1f)
        placeUnplacedPanels()
    }

    /**
     * First open (or new category): a grid with as many columns as fit, and the rows sharing the
     * height, so every category is on screen even in a small window. Panels scroll when their row is
     * shorter than their module list (see [updatePanelLimits]).
     */
    private fun placeUnplacedPanels() {
        val unplaced = panels.filter { it.state.x.value < 0 || it.state.y.value < 0 }
        if (unplaced.isEmpty()) return
        val gap = 8
        val columns = ((width - 20 + gap) / (Style.PANEL_WIDTH + gap)).coerceIn(1, unplaced.size)
        val rows = (unplaced.size + columns - 1) / columns
        val rowHeight = ((height - TOP - gap) / rows).coerceAtLeast(Style.HEADER_HEIGHT + Style.MODULE_HEIGHT)
        // Centre the grid horizontally.
        val left = ((width - (columns * Style.PANEL_WIDTH + (columns - 1) * gap)) / 2).coerceAtLeast(10)
        unplaced.forEachIndexed { index, panel ->
            panel.state.x.value = left + (index % columns) * (Style.PANEL_WIDTH + gap)
            panel.state.y.value = TOP + (index / columns) * rowHeight
        }
    }

    /** Each panel ends above the nearest panel below it in the same column, so lists scroll instead of overlapping. */
    private fun updatePanelLimits() {
        for (panel in panels) {
            panel.bottomLimit = panels
                .filter { it !== panel && it.y > panel.y && it.x < panel.x + panel.width && it.x + it.width > panel.x }
                .minOfOrNull { it.y - 6 } ?: Int.MAX_VALUE
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        // Glass needs something to frost: the world in-game, the menu panorama on the title screen.
        if (minecraft.level == null) extractPanorama(graphics, a)
        if (ClickGui.blur) extractBlurredBackground(graphics)
        Draw.rect(graphics, 0, 0, width, height, Colors.fade(Glass.BACKDROP, opening.value.coerceIn(0f, 1f)))
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, rawMouseX: Int, rawMouseY: Int, a: Float) {
        val (mouseX, mouseY) = DebugHooks.fakeMouse ?: (rawMouseX to rawMouseY)
        updatePanelLimits()
        val filter = search.trim()
        panels.forEach { panel ->
            panel.filter = if (filter.isEmpty()) {
                { true }
            } else {
                { it.name.contains(filter, ignoreCase = true) || it.description.contains(filter, ignoreCase = true) }
            }
        }

        // Open animation: a slight zoom from the centre.
        val scale = 0.94f + 0.06f * opening.value
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(width / 2f, height / 2f)
        pose.scale(scale, scale)
        pose.translate(-width / 2f, -height / 2f)

        renderSearchBar(graphics)
        for (panel in panels) {
            if (filter.isNotEmpty() && panel.visibleButtons.isEmpty()) continue
            panel.render(graphics, mouseX, mouseY, height)
        }
        pose.popMatrix()

        // Tooltip of whatever is under the cursor (topmost panel first).
        val tooltip = panels.asReversed().firstOrNull { it.isOver(mouseX.toDouble(), mouseY.toDouble(), height) }
            ?.hoveredElement(mouseX, mouseY, height)?.tooltip()
        renderTooltip(graphics, tooltip, mouseX, mouseY)
    }

    private var tooltipText: String? = null
    private var tooltipSince = 0L

    /** Dark glass tooltip in the UI font, shown after a short hover and kept on screen. */
    private fun renderTooltip(g: GuiGraphicsExtractor, text: String?, mouseX: Int, mouseY: Int) {
        if (text != tooltipText) {
            tooltipText = text
            tooltipSince = System.currentTimeMillis()
        }
        if (text == null) return
        val shownFor = System.currentTimeMillis() - tooltipSince - TOOLTIP_DELAY
        if (shownFor < 0) return
        val alpha = (shownFor / TOOLTIP_FADE.toFloat()).coerceIn(0f, 1f)

        val lines = wrap(text, TOOLTIP_MAX_WIDTH)
        val lineHeight = 10
        val w = lines.maxOf { Draw.width(it) } + 12
        val h = lines.size * lineHeight + 8
        val x = (mouseX + 10).coerceAtMost(width - w - 4)
        val y = (mouseY + 10).let { if (it + h > height - 4) mouseY - h - 4 else it }
        Draw.glass(g, x, y, w, h, 6, tint = Glass.TOOLTIP, alpha = alpha)
        lines.forEachIndexed { i, line -> Draw.text(g, line, x + 6, y + 5 + i * lineHeight, Colors.fade(Glass.TEXT, alpha)) }
    }

    private fun wrap(text: String, maxWidth: Int): List<String> {
        val lines = ArrayList<String>()
        var current = ""
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (Draw.width(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun renderSearchBar(g: GuiGraphicsExtractor) {
        val x = (width - SEARCH_WIDTH) / 2
        Draw.glass(g, x, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT, SEARCH_HEIGHT / 2, tint = if (searchFocused) Glass.BODY_STRONG else Glass.BODY)
        if (searchFocused) Draw.roundedRing(g, x, SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT, SEARCH_HEIGHT / 2, Colors.withAlpha(Style.accent, 200))
        // Magnifier: a small ring with a handle.
        Draw.roundedRing(g, x + 10, SEARCH_Y + 6, 8, 8, 4, Glass.TEXT_DIM)
        Draw.rect(g, x + 17, SEARCH_Y + 13, 2, 2, Glass.TEXT_DIM)
        val textY = Style.textY(SEARCH_Y, SEARCH_HEIGHT)
        val caret = if (searchFocused && System.currentTimeMillis() / 500 % 2 == 0L) "|" else ""
        if (search.isEmpty() && !searchFocused) {
            Draw.text(g, "Search", x + 24, textY, Glass.TEXT_MUTED)
        } else {
            Draw.text(g, Draw.ellipsize(search, SEARCH_WIDTH - 36) + caret, x + 24, textY, Glass.TEXT)
        }
    }

    private fun isOverSearch(mouseX: Double, mouseY: Double): Boolean {
        val x = (width - SEARCH_WIDTH) / 2
        return mouseX >= x && mouseX < x + SEARCH_WIDTH && mouseY >= SEARCH_Y && mouseY < SEARCH_Y + SEARCH_HEIGHT
    }

    private companion object {
        const val SEARCH_WIDTH = 200
        const val SEARCH_HEIGHT = 20
        const val SEARCH_Y = 8

        /** Where panels start, below the search bar. */
        const val TOP = 36

        const val TOOLTIP_DELAY = 350L
        const val TOOLTIP_FADE = 120L
        const val TOOLTIP_MAX_WIDTH = 180
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = event.buttonInfo().button()
        searchFocused = isOverSearch(mouseX, mouseY)
        if (searchFocused) {
            Focus.element = null
            return true
        }
        // Topmost panel wins; clicking a panel brings it to the front.
        val panel = panels.asReversed().firstOrNull { it.isOver(mouseX, mouseY, height) }
        if (panel == null) {
            Focus.element = null
            return super.mouseClicked(event, doubleClick)
        }
        val focusBefore = Focus.element
        panels.remove(panel)
        panels.add(panel)
        val handled = panel.mouseClicked(mouseX, mouseY, button, height)
        if (Focus.element === focusBefore && focusBefore != null && !focusBefore.isHovered(mouseX, mouseY)) Focus.element = null
        return handled
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        panels.forEach { it.mouseReleased(event.x(), event.y(), event.buttonInfo().button()) }
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        panels.forEach { it.mouseDragged(event.x(), event.y(), event.buttonInfo().button(), width, height) }
        return true
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        panels.asReversed().firstOrNull { it.isOver(x, y, height) }?.scroll(scrollY)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyName = InputConstants.getKey(event).name
        Focus.element?.let { if (it.keyPressed(keyName)) return true }
        if (searchFocused) {
            when {
                keyName == "key.keyboard.backspace" -> search = search.dropLast(1)
                event.isEscape -> {
                    search = ""
                    searchFocused = false
                }
                else -> return super.keyPressed(event)
            }
            return true
        }
        // The key that opens the menu also closes it.
        if (keyName == ClickGui.bind.value.key) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val text = event.codepointAsString()
        Focus.element?.let { if (it.charTyped(text)) return true }
        if (text.isBlank() && !searchFocused) return false
        // Typing anywhere starts a search.
        searchFocused = true
        search = (search + text).take(32)
        return true
    }

    override fun removed() {
        Focus.element = null
        DyroxClient.config.markDirty()
    }
}
