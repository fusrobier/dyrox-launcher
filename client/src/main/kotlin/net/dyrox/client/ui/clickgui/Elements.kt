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
import net.dyrox.client.render.Glass
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.awt.Color
import kotlin.math.roundToInt

/** Sizes of the Liquid Glass ClickGUI; colours come from [Glass] and the accent setting. */
object Style {
    const val PANEL_WIDTH = 126
    const val HEADER_HEIGHT = 24
    const val MODULE_HEIGHT = 18
    const val ROW_HEIGHT = 15
    const val PADDING = 7
    const val RADIUS = 12
    const val ROW_RADIUS = 7

    val accent: Int get() = ClickGui.accent

    /** Y offset that vertically centres one line of UI text in a row of [rowHeight]. */
    fun textY(rowTop: Int, rowHeight: Int) = rowTop + (rowHeight - 8) / 2
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

/** A small rounded chip showing a value (choice, key, mode). */
private fun chip(g: GuiGraphicsExtractor, text: String, right: Int, rowTop: Int, rowHeight: Int, textColor: Int, maxWidth: Int) {
    val shown = Draw.ellipsize(text, maxWidth)
    val w = Draw.width(shown) + 10
    val h = rowHeight - 3
    val x = right - w
    Draw.roundedRect(g, x, rowTop + 1, w, h, h / 2, Glass.HOVER)
    Draw.roundedRing(g, x, rowTop + 1, w, h, h / 2, Glass.DIVIDER)
    Draw.text(g, shown, x + 5, Style.textY(rowTop, rowHeight), textColor)
}

abstract class SettingElement<T>(val value: Value<T>) : Element() {
    override val visible: Boolean get() = value.visibleWhen()
    override val height: Int get() = Style.ROW_HEIGHT
    override fun tooltip(): String? = value.description.ifBlank { null }

    protected fun label(g: GuiGraphicsExtractor, hovered: Boolean, text: String = value.name) =
        Draw.text(g, Draw.ellipsize(text, width / 2 + 4), x + Style.PADDING, Style.textY(y, Style.ROW_HEIGHT), if (hovered) Glass.TEXT else Glass.TEXT_DIM)

    protected fun valueText(g: GuiGraphicsExtractor, text: String, color: Int = Glass.TEXT_DIM) {
        val shown = Draw.ellipsize(text, width / 2 - Style.PADDING)
        Draw.text(g, shown, x + width - Style.PADDING - Draw.width(shown), Style.textY(y, Style.ROW_HEIGHT), color)
    }
}

/** iOS-style switch: capsule track, white knob. */
class ToggleElement(value: BooleanValue) : SettingElement<Boolean>(value) {
    private val knob = Animated(if (value.value) 1f else 0f, ClickGui.duration(160f))

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        knob.animateTo(if (value.value) 1f else 0f)
        val t = knob.value
        label(g, isHovered(mouseX, mouseY))
        val trackWidth = 20
        val trackHeight = 11
        val tx = x + width - Style.PADDING - trackWidth
        val ty = y + (height - trackHeight) / 2
        Draw.roundedRect(g, tx, ty, trackWidth, trackHeight, trackHeight / 2, Colors.lerp(Glass.TRACK, Style.accent, t))
        val knobX = tx + 5 + ((trackWidth - 10) * t).roundToInt()
        Draw.circle(g, knobX, ty + 6, 5, 0x30000000)
        Draw.circle(g, knobX, ty + 5, 4, Glass.KNOB)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != Buttons.LEFT) return false
        value.value = !value.value
        return true
    }
}

/** Capsule track with an accent fill and a white knob; drag or click. */
private fun sliderTrack(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, from: Float, to: Float, knobs: List<Pair<Float, Boolean>>) {
    Draw.roundedRect(g, x, y, width, 4, 2, Glass.TRACK)
    val start = x + (width * from).roundToInt()
    val end = x + (width * to).roundToInt()
    Draw.roundedRect(g, start, y, (end - start).coerceAtLeast(2), 4, 2, Style.accent)
    for ((position, active) in knobs) {
        val kx = x + (width * position).roundToInt()
        val radius = if (active) 5 else 4
        Draw.circle(g, kx, y + 3, radius + 1, 0x30000000)
        Draw.circle(g, kx, y + 2, radius, Glass.KNOB)
    }
}

class SliderElement<N : Number>(
    value: Value<N>,
    private val min: Double,
    private val max: Double,
    private val format: (N) -> String,
    private val set: (Double) -> Unit,
) : SettingElement<N>(value) {
    private var dragging = false
    private val fill = Animated(fraction(), ClickGui.duration(90f))

    override val height: Int get() = Style.ROW_HEIGHT + 9

    private fun fraction(): Float = ((value.value.toDouble() - min) / (max - min)).toFloat().coerceIn(0f, 1f)

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g, isHovered(mouseX, mouseY) || dragging)
        valueText(g, format(value.value), Glass.TEXT)
        fill.animateTo(fraction())
        val f = fill.value
        sliderTrack(g, x + Style.PADDING, y + Style.ROW_HEIGHT + 2, width - 2 * Style.PADDING, 0f, f, listOf(f to dragging))
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

    override val height: Int get() = Style.ROW_HEIGHT + 9

    private fun fractionOf(v: Double) = ((v - bounds.start) / (bounds.endInclusive - bounds.start)).toFloat().coerceIn(0f, 1f)

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g, isHovered(mouseX, mouseY) || dragging != 0)
        valueText(g, format(value.value), Glass.TEXT)
        val (low, high) = get(value.value)
        val lo = fractionOf(low)
        val hi = fractionOf(high)
        sliderTrack(g, x + Style.PADDING, y + Style.ROW_HEIGHT + 2, width - 2 * Style.PADDING, lo, hi, listOf(lo to (dragging == 1), hi to (dragging == 2)))
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
        label(g, isHovered(mouseX, mouseY))
        chip(g, choice.value.choiceName, x + width - Style.PADDING, y, Style.ROW_HEIGHT, Style.accent, width / 2 - 12)
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

/** A header row, then one check row per option. */
class MultiChoiceElement<E : NamedChoice>(private val multi: MultiChoiceValue<E>) : SettingElement<Set<E>>(multi) {
    private val optionHeight = 13

    override val height: Int get() = Style.ROW_HEIGHT + multi.choices.size * optionHeight

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g, false)
        valueText(g, "${multi.value.size}/${multi.choices.size}", Glass.TEXT_MUTED)
        multi.choices.forEachIndexed { index, option ->
            val rowY = y + Style.ROW_HEIGHT + index * optionHeight
            val selected = option in multi.value
            val hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + optionHeight
            val box = x + Style.PADDING + 3
            Draw.roundedRect(g, box, rowY + 2, 9, 9, 3, if (selected) Style.accent else Glass.TRACK)
            if (selected) Draw.circle(g, box + 4, rowY + 6, 2, Glass.KNOB)
            Draw.text(g, option.choiceName, box + 14, Style.textY(rowY, optionHeight), if (selected || hovered) Glass.TEXT else Glass.TEXT_MUTED)
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
    private val open = Animated(0f, ClickGui.duration(180f))
    private var dragging = -1
    private val channels = listOf("Hue", "Sat", "Bri", "Alpha")
    private val channelHeight = 12

    private val expandedHeight get() = channels.size * channelHeight + 15

    override val height: Int get() = Style.ROW_HEIGHT + (expandedHeight * open.value).roundToInt()

    private fun hsb(): FloatArray {
        val c = color.value
        return Color.RGBtoHSB(c.red, c.green, c.blue, null)
    }

    private val trackX get() = x + Style.PADDING + 30
    private val trackWidth get() = width - Style.PADDING * 2 - 30

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        open.animateTo(if (expanded) 1f else 0f)
        label(g, mouseY in y until y + Style.ROW_HEIGHT && mouseX in x until x + width)
        val swatchX = x + width - Style.PADDING - 7
        Draw.circle(g, swatchX, y + Style.ROW_HEIGHT / 2, 5, color.value.argb or (0xFF shl 24))
        Draw.roundedRing(g, swatchX - 5, y + Style.ROW_HEIGHT / 2 - 5, 10, 10, 5, Glass.RIM)
        if (color.value.rainbow) Draw.text(g, "~", swatchX - 15, Style.textY(y, Style.ROW_HEIGHT), Style.accent)
        if (open.value <= 0.01f) return

        g.enableScissor(x, y + Style.ROW_HEIGHT, x + width, y + height)
        val hsb = hsb()
        val values = floatArrayOf(hsb[0], hsb[1], hsb[2], color.value.alpha / 255f)
        channels.forEachIndexed { i, name ->
            val rowY = y + Style.ROW_HEIGHT + i * channelHeight
            Draw.text(g, name, x + Style.PADDING + 3, Style.textY(rowY, channelHeight), Glass.TEXT_MUTED)
            if (i == 0) {
                // Hue strip, then the knob on top.
                for (step in 0 until trackWidth) Draw.rect(g, trackX + step, rowY + 5, 1, 3, Color.HSBtoRGB(step / trackWidth.toFloat(), 0.8f, 1f))
                val kx = trackX + (trackWidth * values[0]).roundToInt()
                Draw.circle(g, kx, rowY + 6, 4, Glass.KNOB)
            } else {
                sliderTrack(g, trackX, rowY + 4, trackWidth, 0f, values[i], listOf(values[i] to (dragging == i)))
            }
        }
        val switchY = y + Style.ROW_HEIGHT + channels.size * channelHeight + 1
        Draw.roundedRect(g, x + Style.PADDING + 3, switchY + 2, 9, 9, 3, if (color.value.rainbow) Style.accent else Glass.TRACK)
        Draw.text(g, "Rainbow", x + Style.PADDING + 17, Style.textY(switchY, 13), Glass.TEXT_DIM)
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
        val t = ((mouseX - trackX) / trackWidth).toFloat().coerceIn(0f, 1f)
        val hsb = hsb()
        val values = mutableListOf(hsb[0], hsb[1], hsb[2], color.value.alpha / 255f).also { it[dragging] = t }
        val rgb = Color.HSBtoRGB(values[0], values[1], values[2]) and 0xFFFFFF
        color.value = color.value.copy(argb = ((values[3] * 255).roundToInt() shl 24) or rgb)
    }
}

/** Single-line text; click to focus, Enter or Esc to finish. */
class TextElement(private val text: TextValue) : SettingElement<String>(text) {
    private val focused get() = Focus.element === this

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        label(g, isHovered(mouseX, mouseY))
        val boxWidth = width / 2 - Style.PADDING
        val boxX = x + width - Style.PADDING - boxWidth
        val h = Style.ROW_HEIGHT - 3
        Draw.roundedRect(g, boxX, y + 1, boxWidth, h, h / 2, if (focused) Glass.BODY_STRONG else Glass.HOVER)
        Draw.roundedRing(g, boxX, y + 1, boxWidth, h, h / 2, if (focused) Style.accent else Glass.DIVIDER)
        val caret = if (focused && System.currentTimeMillis() / 500 % 2 == 0L) "|" else ""
        val shown = text.value.takeLast(40)
        val fitted = if (Draw.width(shown + caret) > boxWidth - 10) "…" + shown.takeLast(8) + caret else shown + caret
        Draw.text(g, fitted, boxX + 5, Style.textY(y, Style.ROW_HEIGHT), Glass.TEXT)
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
        label(g, isHovered(mouseX, mouseY), "Bind")
        val text = when {
            listening -> "Press a key…"
            key.value.mode == BindMode.HOLD && key.value.isBound -> KeyNames.label(key.value.key) + " · hold"
            else -> KeyNames.label(key.value.key)
        }
        chip(g, text, x + width - Style.PADDING, y, Style.ROW_HEIGHT, if (listening) Style.accent else Glass.TEXT, width / 2 - 12)
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

/** Mode chip, followed by the active mode's own settings (indented). */
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
        label(g, mouseY in y until y + Style.ROW_HEIGHT && mouseX in x until x + width)
        chip(g, mode.value.name, x + width - Style.PADDING, y, Style.ROW_HEIGHT, Style.accent, width / 2 - 12)
        var childY = y + Style.ROW_HEIGHT
        for (child in children.filter { it.visible }) {
            child.x = x + 5
            child.y = childY
            child.width = width - 5
            child.render(g, mouseX, mouseY)
            childY += child.height
        }
        if (children.isNotEmpty()) Draw.roundedRect(g, x + 3, y + Style.ROW_HEIGHT + 1, 2, childY - y - Style.ROW_HEIGHT - 2, 1, Colors.withAlpha(Style.accent, 110))
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

    fun formatFloat(value: Float, step: Float): String {
        val decimals = when {
            step >= 1f -> 0
            step >= 0.1f -> 1
            else -> 2
        }
        return String.format(java.util.Locale.ROOT, "%.${decimals}f", value)
    }
}
