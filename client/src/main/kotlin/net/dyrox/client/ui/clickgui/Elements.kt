package net.dyrox.client.ui.clickgui

import com.mojang.blaze3d.platform.InputConstants
import net.dyrox.client.config.BindMode
import net.dyrox.client.config.BooleanValue
import net.dyrox.client.config.ChoiceValue
import net.dyrox.client.config.ColorValue
import net.dyrox.client.config.DyroxColor
import net.dyrox.client.config.FloatRangeValue
import net.dyrox.client.config.FloatValue
import net.dyrox.client.config.IntRangeValue
import net.dyrox.client.config.IntValue
import net.dyrox.client.config.KeyBind
import net.dyrox.client.config.KeyValue
import net.dyrox.client.config.ModeValue
import net.dyrox.client.config.MultiChoiceValue
import net.dyrox.client.config.NamedChoice
import net.dyrox.client.config.TextValue
import net.dyrox.client.config.Value
import net.dyrox.client.input.KeyNames
import net.dyrox.client.module.Module
import net.dyrox.client.module.modules.render.ClickGui
import net.dyrox.client.render.Animated
import net.dyrox.client.render.Colors
import net.dyrox.client.render.Draw
import net.dyrox.shared.theme.DyroxPalette
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.awt.Color
import kotlin.math.roundToInt

/** Sizes and colours of the ClickGUI. */
object Style {
    const val PANEL_WIDTH = 118
    const val HEADER_HEIGHT = 20
    const val MODULE_HEIGHT = 16
    const val ROW_HEIGHT = 14
    const val PADDING = 5
    const val RADIUS = 6

    const val PANEL = DyroxPalette.SURFACE
    const val HEADER = DyroxPalette.SURFACE_ELEVATED
    const val HOVER = DyroxPalette.SURFACE_HIGHLIGHT
    const val BORDER = DyroxPalette.BORDER
    const val TEXT = DyroxPalette.TEXT_PRIMARY
    const val TEXT_DIM = DyroxPalette.TEXT_SECONDARY
    const val TEXT_MUTED = DyroxPalette.TEXT_MUTED
    const val TRACK = DyroxPalette.SURFACE_HIGHLIGHT

    val accent: Int get() = ClickGui.accent
}

/** Something laid out in a panel column. Positions are assigned by the parent every frame. */
abstract class Element {
    var x = 0
    var y = 0
    var width = 0

    abstract val height: Int
    open val visible: Boolean get() = true

    abstract fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int)
    open fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean = false
    open fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {}
    open fun mouseDragged(mouseX: Double, mouseY: Double, button: Int) {}

    /** Only called on the focused element. Returns true if handled. */
    open fun keyPressed(keyName: String): Boolean = false
    open fun charTyped(text: String): Boolean = false
    open fun onFocusLost() {}

    /** Tooltip for the hovered element. */
    open fun tooltip(): String? = null

    fun isHovered(mouseX: Number, mouseY: Number): Boolean {
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        return mx >= x && mx < x + width && my >= y && my < y + height
    }
}

/** Keyboard focus for text fields and key capture. */
object Focus {
    var element: Element? = null
        set(value) {
            if (field !== value) field?.onFocusLost()
            field = value
        }
}

object Buttons {
    const val LEFT = InputConstants.MOUSE_BUTTON_LEFT
    const val RIGHT = InputConstants.MOUSE_BUTTON_RIGHT
}

abstract class SettingElement<T>(val value: Value<T>) : Element() {
    override val visible: Boolean get() = value.visibleWhen()
    override val height: Int get() = Style.ROW_HEIGHT
    override fun tooltip(): String? = value.description.ifBlank { null }

    protected fun label(g: GuiGraphicsExtractor, text: String = value.name, color: Int = Style.TEXT_DIM) =
        Draw.text(g, Draw.ellipsize(text, width - 2 * Style.PADDING - 40), x + Style.PADDING, y + (Style.ROW_HEIGHT - 8) / 2, color)

    protected fun valueText(g: GuiGraphicsExtractor, text: String, color: Int = Style.TEXT) {
        val shown = Draw.ellipsize(text, width / 2)
        Draw.text(g, shown, x + width - Style.PADDING - Draw.width(shown), y + (Style.ROW_HEIGHT - 8) / 2, color)
    }
}

class ToggleElement(value: BooleanValue) : SettingElement<Boolean>(value) {
    private val knob = Animated(if (value.value) 1f else 0f, ClickGui.duration(140f))

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        knob.animateTo(if (value.value) 1f else 0f)
        val t = knob.value
        label(g, color = if (isHovered(mouseX, mouseY)) Style.TEXT else Style.TEXT_DIM)
        val switchWidth = 16
        val sx = x + width - Style.PADDING - switchWidth
        val sy = y + (height - 8) / 2
        Draw.roundedRect(g, sx, sy, switchWidth, 8, 4, Colors.lerp(Style.TRACK, Style.accent, t))
        Draw.circle(g, sx + 4 + ((switchWidth - 8) * t).roundToInt(), sy + 4, 3, Colors.lerp(Style.TEXT_DIM, DyroxPalette.ON_ACCENT, t))
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != Buttons.LEFT) return false
        value.value = !value.value
        return true
    }
}

/** Int or float slider; drag or click the track. */
class SliderElement<N : Number>(
    value: Value<N>,
    private val min: Double,
    private val max: Double,
    private val format: (N) -> String,
    private val set: (Double) -> Unit,
) : SettingElement<N>(value) {
    private var dragging = false
    private val fill = Animated(fraction(), ClickGui.duration(90f))

    override val height: Int get() = Style.ROW_HEIGHT + 6

    private fun fraction(): Float = ((value.value.toDouble() - min) / (max - min)).toFloat().coerceIn(0f, 1f)

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g, color = if (isHovered(mouseX, mouseY) || dragging) Style.TEXT else Style.TEXT_DIM)
        valueText(g, format(value.value))
        fill.animateTo(fraction())
        val trackX = x + Style.PADDING
        val trackWidth = width - 2 * Style.PADDING
        val trackY = y + Style.ROW_HEIGHT + 1
        Draw.roundedRect(g, trackX, trackY, trackWidth, 3, 1, Style.TRACK)
        val filled = (trackWidth * fill.value).roundToInt()
        Draw.roundedRect(g, trackX, trackY, filled, 3, 1, Style.accent)
        Draw.circle(g, trackX + filled, trackY + 1, if (dragging) 3 else 2, Style.TEXT)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != Buttons.LEFT) return false
        dragging = true
        update(mouseX)
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int) {
        if (dragging) update(mouseX)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
        dragging = false
    }

    private fun update(mouseX: Double) {
        val t = ((mouseX - x - Style.PADDING) / (width - 2 * Style.PADDING)).coerceIn(0.0, 1.0)
        set(min + (max - min) * t)
    }
}

/** Min–max slider: drags whichever end is closer to the cursor. */
class RangeSliderElement<R>(
    value: Value<R>,
    private val bounds: ClosedFloatingPointRange<Double>,
    private val get: (R) -> Pair<Double, Double>,
    private val format: (R) -> String,
    private val set: (Double, Double) -> Unit,
) : SettingElement<R>(value) {
    private var dragging = 0 // 0 none, 1 low, 2 high

    override val height: Int get() = Style.ROW_HEIGHT + 6

    private fun fractionOf(v: Double) = ((v - bounds.start) / (bounds.endInclusive - bounds.start)).toFloat().coerceIn(0f, 1f)

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g, color = if (isHovered(mouseX, mouseY) || dragging != 0) Style.TEXT else Style.TEXT_DIM)
        valueText(g, format(value.value))
        val (low, high) = get(value.value)
        val trackX = x + Style.PADDING
        val trackWidth = width - 2 * Style.PADDING
        val trackY = y + Style.ROW_HEIGHT + 1
        val lowX = trackX + (trackWidth * fractionOf(low)).roundToInt()
        val highX = trackX + (trackWidth * fractionOf(high)).roundToInt()
        Draw.roundedRect(g, trackX, trackY, trackWidth, 3, 1, Style.TRACK)
        Draw.roundedRect(g, lowX, trackY, (highX - lowX).coerceAtLeast(1), 3, 1, Style.accent)
        Draw.circle(g, lowX, trackY + 1, if (dragging == 1) 3 else 2, Style.TEXT)
        Draw.circle(g, highX, trackY + 1, if (dragging == 2) 3 else 2, Style.TEXT)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != Buttons.LEFT) return false
        val (low, high) = get(value.value)
        val trackWidth = width - 2 * Style.PADDING
        val lowX = x + Style.PADDING + trackWidth * fractionOf(low)
        val highX = x + Style.PADDING + trackWidth * fractionOf(high)
        dragging = if (kotlin.math.abs(mouseX - lowX) <= kotlin.math.abs(mouseX - highX)) 1 else 2
        update(mouseX)
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int) {
        if (dragging != 0) update(mouseX)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
        dragging = 0
    }

    private fun update(mouseX: Double) {
        val t = ((mouseX - x - Style.PADDING) / (width - 2 * Style.PADDING)).coerceIn(0.0, 1.0)
        val v = bounds.start + (bounds.endInclusive - bounds.start) * t
        val (low, high) = get(value.value)
        if (dragging == 1) set(minOf(v, high), high) else set(low, maxOf(v, low))
    }
}

/** Left click: next choice, right click: previous. */
class ChoiceElement<E : NamedChoice>(private val choice: ChoiceValue<E>) : SettingElement<E>(choice) {
    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g, color = if (isHovered(mouseX, mouseY)) Style.TEXT else Style.TEXT_DIM)
        valueText(g, choice.value.choiceName, Style.accent)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        when (button) {
            Buttons.LEFT -> choice.cycle(forward = true)
            Buttons.RIGHT -> choice.cycle(forward = false)
            else -> return false
        }
        return true
    }
}

/** A header row, then one checkbox row per option. */
class MultiChoiceElement<E : NamedChoice>(private val multi: MultiChoiceValue<E>) : SettingElement<Set<E>>(multi) {
    private val optionHeight = 12

    override val height: Int get() = Style.ROW_HEIGHT + multi.choices.size * optionHeight

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g)
        valueText(g, "${multi.value.size}/${multi.choices.size}", Style.TEXT_MUTED)
        multi.choices.forEachIndexed { index, option ->
            val rowY = y + Style.ROW_HEIGHT + index * optionHeight
            val selected = option in multi.value
            val hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + optionHeight
            Draw.roundedRect(g, x + Style.PADDING + 4, rowY + 2, 7, 7, 2, if (selected) Style.accent else Style.TRACK)
            Draw.text(g, option.choiceName, x + Style.PADDING + 15, rowY + 2, if (selected || hovered) Style.TEXT else Style.TEXT_MUTED)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != Buttons.LEFT) return false
        val index = ((mouseY - y - Style.ROW_HEIGHT) / optionHeight).toInt()
        if (mouseY < y + Style.ROW_HEIGHT || index !in multi.choices.indices) return false
        multi.toggle(multi.choices[index])
        return true
    }
}

/** Swatch; click to expand hue / saturation / brightness / opacity sliders and the rainbow switch. */
class ColorElement(private val color: ColorValue) : SettingElement<DyroxColor>(color) {
    private var expanded = false
    private val open = Animated(0f, ClickGui.duration(160f))
    private var dragging = -1
    private val channels = listOf("Hue", "Saturation", "Brightness", "Opacity")
    private val channelHeight = 11

    private val expandedHeight get() = channels.size * channelHeight + 13

    override val height: Int get() = Style.ROW_HEIGHT + (expandedHeight * open.value).roundToInt()

    private fun hsb(): FloatArray {
        val c = color.value
        return Color.RGBtoHSB(c.red, c.green, c.blue, null)
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        open.animateTo(if (expanded) 1f else 0f)
        label(g, color = if (isHovered(mouseX, mouseY)) Style.TEXT else Style.TEXT_DIM)
        Draw.roundedOutlined(g, x + width - Style.PADDING - 16, y + 3, 16, 8, 3, color.value.argb or (0xFF shl 24), Style.BORDER)
        if (color.value.rainbow) Draw.text(g, "~", x + width - Style.PADDING - 24, y + 3, Style.accent)
        if (open.value <= 0.01f) return

        g.enableScissor(x, y + Style.ROW_HEIGHT, x + width, y + height)
        val hsb = hsb()
        val values = floatArrayOf(hsb[0], hsb[1], hsb[2], color.value.alpha / 255f)
        channels.forEachIndexed { i, name ->
            val rowY = y + Style.ROW_HEIGHT + i * channelHeight
            Draw.text(g, name.take(3), x + Style.PADDING + 4, rowY + 2, Style.TEXT_MUTED)
            val trackX = x + Style.PADDING + 26
            val trackWidth = width - Style.PADDING * 2 - 26
            if (i == 0) {
                // Hue strip.
                for (step in 0 until trackWidth) Draw.rect(g, trackX + step, rowY + 4, 1, 3, Color.HSBtoRGB(step / trackWidth.toFloat(), 1f, 1f))
            } else {
                Draw.roundedRect(g, trackX, rowY + 4, trackWidth, 3, 1, Style.TRACK)
                Draw.roundedRect(g, trackX, rowY + 4, (trackWidth * values[i]).roundToInt(), 3, 1, Style.accent)
            }
            Draw.circle(g, trackX + (trackWidth * values[i]).roundToInt(), rowY + 5, 2, Style.TEXT)
        }
        val switchY = y + Style.ROW_HEIGHT + channels.size * channelHeight
        Draw.roundedRect(g, x + Style.PADDING + 4, switchY + 3, 7, 7, 2, if (color.value.rainbow) Style.accent else Style.TRACK)
        Draw.text(g, "Rainbow", x + Style.PADDING + 15, switchY + 3, Style.TEXT_DIM)
        g.disableScissor()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseY < y + Style.ROW_HEIGHT) {
            expanded = !expanded
            return true
        }
        if (!expanded || button != Buttons.LEFT) return false
        val row = ((mouseY - y - Style.ROW_HEIGHT) / channelHeight).toInt()
        if (row in channels.indices) {
            dragging = row
            update(mouseX)
        } else {
            color.value = color.value.copy(rainbow = !color.value.rainbow)
        }
        return true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int) {
        if (dragging >= 0) update(mouseX)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
        dragging = -1
    }

    private fun update(mouseX: Double) {
        val trackX = x + Style.PADDING + 26
        val trackWidth = width - Style.PADDING * 2 - 26
        val t = ((mouseX - trackX) / trackWidth).toFloat().coerceIn(0f, 1f)
        val hsb = hsb()
        val alpha = color.value.alpha
        val (h, s, b, a) = listOf(hsb[0], hsb[1], hsb[2], alpha / 255f).toMutableList().also { it[dragging] = t }
        val rgb = Color.HSBtoRGB(h, s, b) and 0xFFFFFF
        color.value = color.value.copy(argb = ((a * 255).roundToInt() shl 24) or rgb)
    }
}

/** Single-line text; click to focus, Enter or Esc to finish. */
class TextElement(private val text: TextValue) : SettingElement<String>(text) {
    private val focused get() = Focus.element === this

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g)
        val boxX = x + width / 2
        val boxWidth = width / 2 - Style.PADDING
        Draw.roundedRect(g, boxX, y + 2, boxWidth, Style.ROW_HEIGHT - 4, 3, if (focused) Style.HOVER else Style.TRACK)
        val caret = if (focused && System.currentTimeMillis() / 500 % 2 == 0L) "_" else ""
        val shown = text.value.takeLast(40)
        val fitted = if (Draw.width(shown + caret) > boxWidth - 4) "…" + shown.takeLast(8) + caret else shown + caret
        Draw.text(g, fitted, boxX + 3, y + 3, Style.TEXT)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        Focus.element = this
        return true
    }

    override fun keyPressed(keyName: String): Boolean {
        when (keyName) {
            "key.keyboard.backspace" -> text.value = text.value.dropLast(1)
            "key.keyboard.enter", "key.keyboard.escape", "key.keyboard.keypad.enter" -> Focus.element = null
            else -> return false
        }
        return true
    }

    override fun charTyped(text: String): Boolean {
        this.text.value += text
        return true
    }
}

/** Click, then press a key. Esc cancels, Backspace/Delete unbinds, right click switches toggle/hold. */
class KeyElement(private val key: KeyValue) : SettingElement<KeyBind>(key) {
    private val listening get() = Focus.element === this

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g, "Bind", if (isHovered(mouseX, mouseY)) Style.TEXT else Style.TEXT_DIM)
        val text = when {
            listening -> "press a key…"
            key.value.mode == BindMode.HOLD && key.value.isBound -> KeyNames.label(key.value.key) + " (hold)"
            else -> KeyNames.label(key.value.key)
        }
        valueText(g, text, if (listening) Style.accent else Style.TEXT)
    }

    override fun tooltip() = "Left click to set, right click for toggle/hold"

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        when (button) {
            Buttons.LEFT -> Focus.element = if (listening) null else this
            Buttons.RIGHT -> key.value = key.value.copy(mode = if (key.value.mode == BindMode.TOGGLE) BindMode.HOLD else BindMode.TOGGLE)
            else -> return false
        }
        return true
    }

    override fun keyPressed(keyName: String): Boolean {
        when (keyName) {
            "key.keyboard.escape" -> Unit
            "key.keyboard.backspace", "key.keyboard.delete" -> key.value = key.value.copy(key = null)
            else -> key.value = key.value.copy(key = keyName)
        }
        Focus.element = null
        return true
    }
}

/** Choice row for the mode, followed by the active mode's own settings (indented). */
class ModeElement(private val mode: ModeValue<*>) : SettingElement<Any?>(@Suppress("UNCHECKED_CAST") (mode as Value<Any?>)) {
    private var children: List<Element> = emptyList()
    private var builtFor: Any? = null

    private fun ensureChildren() {
        if (builtFor !== mode.value) {
            children = SettingElements.forValues(mode.value.values)
            builtFor = mode.value
        }
    }

    override val height: Int
        get() {
            ensureChildren()
            return Style.ROW_HEIGHT + children.filter { it.visible }.sumOf { it.height }
        }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        ensureChildren()
        label(g, color = if (mouseY in y until y + Style.ROW_HEIGHT && mouseX in x until x + width) Style.TEXT else Style.TEXT_DIM)
        valueText(g, mode.value.name, Style.accent)
        var childY = y + Style.ROW_HEIGHT
        for (child in children.filter { it.visible }) {
            child.x = x + 4
            child.y = childY
            child.width = width - 4
            child.render(g, mouseX, mouseY)
            childY += child.height
        }
        if (children.isNotEmpty()) Draw.rect(g, x + 2, y + Style.ROW_HEIGHT, 1, childY - y - Style.ROW_HEIGHT, Colors.withAlpha(Style.accent, 90))
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (mouseY < y + Style.ROW_HEIGHT) {
            val modes = mode.modes
            val index = modes.indexOf(mode.value)
            val next = when (button) {
                Buttons.LEFT -> modes[(index + 1) % modes.size]
                Buttons.RIGHT -> modes[(index + modes.size - 1) % modes.size]
                else -> return false
            }
            mode.select(next.name)
            return true
        }
        return children.filter { it.visible }.firstOrNull { it.isHovered(mouseX, mouseY) }?.mouseClicked(mouseX, mouseY, button) ?: false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int) = children.forEach { it.mouseDragged(mouseX, mouseY, button) }
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) = children.forEach { it.mouseReleased(mouseX, mouseY, button) }
}

object SettingElements {
    /** Builds widgets for a module's (or mode's) settings. "Enabled" is the module row itself, so it's skipped. */
    fun forValues(values: List<Value<*>>): List<Element> = values.mapNotNull { forValue(it) }

    @Suppress("UNCHECKED_CAST")
    fun forValue(value: Value<*>): Element? = when (value) {
        is BooleanValue -> if (value.name == Module.ENABLED) null else ToggleElement(value)
        is IntValue -> SliderElement(value, value.range.first.toDouble(), value.range.last.toDouble(), { it.toString() }) { value.value = it.roundToInt() }
        is FloatValue -> SliderElement(value, value.range.start.toDouble(), value.range.endInclusive.toDouble(), { formatFloat(it, value.step) }) { value.value = it.toFloat() }
        is FloatRangeValue -> RangeSliderElement(
            value, value.bounds.start.toDouble()..value.bounds.endInclusive.toDouble(),
            { it.start.toDouble() to it.endInclusive.toDouble() },
            { "${formatFloat(it.start, value.step)}–${formatFloat(it.endInclusive, value.step)}" },
        ) { low, high -> value.value = low.toFloat()..high.toFloat() }
        is IntRangeValue -> RangeSliderElement(
            value, value.bounds.first.toDouble()..value.bounds.last.toDouble(),
            { it.first.toDouble() to it.last.toDouble() },
            { "${it.first}–${it.last}" },
        ) { low, high -> value.value = low.roundToInt()..high.roundToInt() }
        is ChoiceValue<*> -> ChoiceElement(value as ChoiceValue<NamedChoice>)
        is MultiChoiceValue<*> -> MultiChoiceElement(value as MultiChoiceValue<NamedChoice>)
        is ColorValue -> ColorElement(value)
        is TextValue -> TextElement(value)
        is KeyValue -> KeyElement(value)
        is ModeValue<*> -> ModeElement(value)
        else -> null
    }

    private fun formatFloat(value: Float, step: Float): String {
        val decimals = when {
            step >= 1f -> 0
            step >= 0.1f -> 1
            else -> 2
        }
        return "%.${decimals}f".format(value)
    }
}
