package net.dyrox.client.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * One setting. Usable as a property delegate: `val range by float("Range", 4.2f, 1f..6f)`.
 * Assignments are validated (clamped, snapped to step) and listeners fire only on real changes.
 */
abstract class Value<T>(
    val name: String,
    val default: T,
    val description: String = "",
) : ReadWriteProperty<Any?, T> {
    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()

    /** Hides the setting in the GUI unless this returns true (e.g. only for one mode). */
    var visibleWhen: () -> Boolean = { true }
        private set

    private var current: T = default

    var value: T
        get() = current
        set(newValue) {
            val validated = validate(newValue)
            if (validated == current) return
            current = validated
            listeners.forEach { it(validated) }
        }

    protected open fun validate(value: T): T = value

    fun onChange(listener: (T) -> Unit): Value<T> = apply { listeners += listener }

    fun visibleIf(condition: () -> Boolean): Value<T> = apply { visibleWhen = condition }

    fun reset() {
        value = default
    }

    /** Nested configurables (a mode setting's modes), for recursive save/load and change tracking. */
    open val children: List<Configurable> get() = emptyList()

    abstract fun toJson(): JsonElement

    /** Throws on malformed input; the caller keeps the current value and logs. */
    abstract fun fromJson(element: JsonElement)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    override fun toString(): String = "$name=$value"
}

class BooleanValue(name: String, default: Boolean, description: String = "") : Value<Boolean>(name, default, description) {
    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) {
        value = element.jsonPrimitive.boolean
    }
}

class IntValue(name: String, default: Int, val range: IntRange, val step: Int = 1, description: String = "") :
    Value<Int>(name, default, description) {
    override fun validate(value: Int): Int {
        val snapped = range.first + ((value - range.first).toDouble() / step).roundToInt() * step
        return snapped.coerceIn(range)
    }

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) {
        value = element.jsonPrimitive.int
    }
}

class FloatValue(
    name: String,
    default: Float,
    val range: ClosedFloatingPointRange<Float>,
    val step: Float = 0.01f,
    description: String = "",
) : Value<Float>(name, default, description) {
    override fun validate(value: Float): Float = snap(value, range, step)

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) {
        value = element.jsonPrimitive.float
    }
}

/** A min–max pair, e.g. click delay 80–120 ms. */
class FloatRangeValue(
    name: String,
    default: ClosedFloatingPointRange<Float>,
    val bounds: ClosedFloatingPointRange<Float>,
    val step: Float = 0.01f,
    description: String = "",
) : Value<ClosedFloatingPointRange<Float>>(name, default, description) {
    override fun validate(value: ClosedFloatingPointRange<Float>): ClosedFloatingPointRange<Float> {
        val a = snap(value.start, bounds, step)
        val b = snap(value.endInclusive, bounds, step)
        return minOf(a, b)..maxOf(a, b)
    }

    override fun toJson() = buildJsonObject {
        put("min", value.start)
        put("max", value.endInclusive)
    }

    override fun fromJson(element: JsonElement) {
        val obj = element.jsonObject
        value = obj.getValue("min").jsonPrimitive.float..obj.getValue("max").jsonPrimitive.float
    }
}

class IntRangeValue(name: String, default: IntRange, val bounds: IntRange, description: String = "") :
    Value<IntRange>(name, default, description) {
    override fun validate(value: IntRange): IntRange {
        val a = value.first.coerceIn(bounds)
        val b = value.last.coerceIn(bounds)
        return minOf(a, b)..maxOf(a, b)
    }

    override fun toJson() = buildJsonObject {
        put("min", value.first)
        put("max", value.last)
    }

    override fun fromJson(element: JsonElement) {
        val obj = element.jsonObject
        value = obj.getValue("min").jsonPrimitive.int..obj.getValue("max").jsonPrimitive.int
    }
}

/** Anything that can be picked from a list; enums implement it to get readable names. */
interface NamedChoice {
    val choiceName: String
}

class ChoiceValue<E : NamedChoice>(name: String, default: E, val choices: List<E>, description: String = "") :
    Value<E>(name, default, description) {
    init {
        require(default in choices) { "Default choice must be one of the choices" }
    }

    override fun validate(value: E): E = if (value in choices) value else default

    fun cycle(forward: Boolean = true) {
        val index = choices.indexOf(value)
        value = choices[(index + if (forward) 1 else choices.size - 1) % choices.size]
    }

    override fun toJson() = JsonPrimitive(value.choiceName)
    override fun fromJson(element: JsonElement) {
        val name = element.jsonPrimitive.content
        value = choices.firstOrNull { it.choiceName.equals(name, ignoreCase = true) } ?: throw IllegalArgumentException("Unknown choice '$name'")
    }
}

class MultiChoiceValue<E : NamedChoice>(name: String, default: Set<E>, val choices: List<E>, description: String = "") :
    Value<Set<E>>(name, default, description) {
    override fun validate(value: Set<E>): Set<E> = value.filterTo(LinkedHashSet()) { it in choices }

    fun toggle(choice: E) {
        value = if (choice in value) value - choice else value + choice
    }

    override fun toJson() = JsonArray(value.map { JsonPrimitive(it.choiceName) })
    override fun fromJson(element: JsonElement) {
        val names = element.jsonArray.map { it.jsonPrimitive.content.lowercase() }.toSet()
        value = choices.filterTo(LinkedHashSet()) { it.choiceName.lowercase() in names }
    }
}

/** ARGB colour with an optional rainbow cycle. */
data class DyroxColor(val argb: Int, val rainbow: Boolean = false) {
    val alpha: Int get() = argb ushr 24
    val red: Int get() = argb shr 16 and 0xFF
    val green: Int get() = argb shr 8 and 0xFF
    val blue: Int get() = argb and 0xFF

    fun toHex(): String = "#%08X".format(argb)

    companion object {
        fun parseHex(hex: String): Int {
            val digits = hex.removePrefix("#")
            val value = digits.toLong(16).toInt()
            return when (digits.length) {
                6 -> value or (0xFF shl 24)
                8 -> value
                else -> throw IllegalArgumentException("Colour must be #RRGGBB or #AARRGGBB")
            }
        }
    }
}

class ColorValue(name: String, default: DyroxColor, description: String = "") : Value<DyroxColor>(name, default, description) {
    override fun toJson() = buildJsonObject {
        put("color", value.toHex())
        put("rainbow", value.rainbow)
    }

    override fun fromJson(element: JsonElement) {
        val obj = element.jsonObject
        value = DyroxColor(DyroxColor.parseHex(obj.getValue("color").jsonPrimitive.content), obj["rainbow"]?.jsonPrimitive?.boolean ?: false)
    }
}

class TextValue(name: String, default: String, val maxLength: Int = 256, description: String = "") : Value<String>(name, default, description) {
    override fun validate(value: String): String = value.take(maxLength)

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) {
        value = element.jsonPrimitive.content
    }
}

enum class BindMode(override val choiceName: String) : NamedChoice {
    /** Press to toggle. */
    TOGGLE("Toggle"),

    /** Enabled only while held. */
    HOLD("Hold"),
}

/** A keybind. [key] is Minecraft's key name (`key.keyboard.r`), or null when unbound. */
data class KeyBind(val key: String?, val mode: BindMode = BindMode.TOGGLE) {
    val isBound: Boolean get() = key != null
}

class KeyValue(name: String, default: KeyBind, description: String = "") : Value<KeyBind>(name, default, description) {
    override fun toJson() = buildJsonObject {
        put("key", value.key)
        put("mode", value.mode.choiceName)
    }

    override fun fromJson(element: JsonElement) {
        val obj = element.jsonObject
        val key = obj["key"]?.jsonPrimitive?.takeIf { it.isString }?.content
        val mode = obj["mode"]?.jsonPrimitive?.content?.let { m -> BindMode.entries.firstOrNull { it.choiceName.equals(m, true) } } ?: BindMode.TOGGLE
        value = KeyBind(key, mode)
    }
}

/**
 * A module mode with its own settings and handlers ("Fly: Vanilla / Glide / Creative").
 * Its handlers only run while it is the selected mode and its owner is running.
 */
abstract class Mode(name: String) : Configurable(name), net.dyrox.client.event.Listenable, NamedChoice {
    override val choiceName: String get() = name

    internal lateinit var modeValue: ModeValue<*>

    override fun handleEvents(): Boolean = modeValue.value === this

    override val parentListenable: net.dyrox.client.event.Listenable? get() = modeValue.owner

    open fun onEnable() {}

    open fun onDisable() {}
}

class ModeValue<M : Mode>(
    name: String,
    val owner: net.dyrox.client.event.Listenable,
    val modes: List<M>,
    default: M,
    description: String = "",
) : Value<M>(name, default, description) {
    init {
        require(modes.isNotEmpty() && default in modes) { "Default mode must be one of the modes" }
        require(modes.map { it.name.lowercase() }.toSet().size == modes.size) { "Mode names must be unique" }
        modes.forEach { it.modeValue = this }
    }

    override val children: List<Configurable> get() = modes

    override fun validate(value: M): M = if (value in modes) value else default

    fun select(name: String): Boolean {
        value = modes.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return false
        return true
    }

    /** `{"active": "Glide", "modes": {"Glide": {...}, "Vanilla": {...}}}` — every mode's settings are kept. */
    override fun toJson() = buildJsonObject {
        put("active", value.name)
        put("modes", JsonObject(modes.associate { it.name to it.toJson() }))
    }

    override fun fromJson(element: JsonElement) {
        val obj = element.jsonObject
        obj["modes"]?.jsonObject?.forEach { (modeName, settings) ->
            modes.firstOrNull { it.name.equals(modeName, true) }?.fromJson(settings.jsonObject) {}
        }
        obj["active"]?.jsonPrimitive?.content?.let { if (!select(it)) throw IllegalArgumentException("Unknown mode '$it'") }
    }
}

private fun snap(value: Float, range: ClosedFloatingPointRange<Float>, step: Float): Float {
    if (value.isNaN()) return range.start
    val steps = ((value - range.start) / step).roundToInt()
    // Round away float noise like 0.30000001 so configs stay tidy.
    val snapped = (range.start + steps * step).toBigDecimal().setScale(4, java.math.RoundingMode.HALF_UP).toFloat()
    return snapped.coerceIn(range)
}
