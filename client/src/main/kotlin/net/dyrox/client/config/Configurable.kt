package net.dyrox.client.config

import kotlinx.serialization.json.JsonObject
import net.dyrox.client.event.Listenable

/** A named group of settings (a module, a mode, the HUD, ...), saved as one JSON object. */
open class Configurable(val name: String) {
    private val registered = ArrayList<Value<*>>()

    val values: List<Value<*>> get() = registered

    fun <V : Value<*>> register(value: V): V {
        require(registered.none { it.name.equals(value.name, ignoreCase = true) }) { "Duplicate setting '${value.name}' in $name" }
        registered += value
        return value
    }

    /** Every value here and in nested configurables (mode settings), depth-first. */
    fun allValues(): List<Value<*>> = values.flatMap { value -> listOf(value) + value.children.flatMap { it.allValues() } }

    protected fun boolean(name: String, default: Boolean, description: String = "") =
        register(BooleanValue(name, default, description))

    protected fun int(name: String, default: Int, range: IntRange, step: Int = 1, description: String = "") =
        register(IntValue(name, default, range, step, description))

    protected fun float(name: String, default: Float, range: ClosedFloatingPointRange<Float>, step: Float = 0.01f, description: String = "") =
        register(FloatValue(name, default, range, step, description))

    protected fun floatRange(
        name: String,
        default: ClosedFloatingPointRange<Float>,
        bounds: ClosedFloatingPointRange<Float>,
        step: Float = 0.01f,
        description: String = "",
    ) = register(FloatRangeValue(name, default, bounds, step, description))

    protected fun intRange(name: String, default: IntRange, bounds: IntRange, description: String = "") =
        register(IntRangeValue(name, default, bounds, description))

    protected fun <E : NamedChoice> choice(name: String, default: E, choices: List<E>, description: String = "") =
        register(ChoiceValue(name, default, choices, description))

    protected inline fun <reified E> choice(name: String, default: E, description: String = ""): ChoiceValue<E>
        where E : Enum<E>, E : NamedChoice = choice(name, default, enumValues<E>().toList(), description)

    protected fun <E : NamedChoice> multiChoice(name: String, default: Set<E>, choices: List<E>, description: String = "") =
        register(MultiChoiceValue(name, default, choices, description))

    protected fun color(name: String, default: DyroxColor, description: String = "") =
        register(ColorValue(name, default, description))

    protected fun text(name: String, default: String, maxLength: Int = 256, description: String = "") =
        register(TextValue(name, default, maxLength, description))

    protected fun key(name: String, default: KeyBind = KeyBind(null), description: String = "") =
        register(KeyValue(name, default, description))

    protected fun <M : Mode> modes(owner: Listenable, name: String, modes: List<M>, default: M = modes.first(), description: String = "") =
        register(ModeValue(name, owner, modes, default, description))

    open fun toJson(): JsonObject = JsonObject(values.associate { it.name to it.toJson() })

    /** Applies known keys; unknown keys are ignored and bad values keep their current value (reported via [onError]). */
    open fun fromJson(json: JsonObject, onError: (String) -> Unit) {
        for (value in values) {
            val element = json.entries.firstOrNull { it.key.equals(value.name, ignoreCase = true) }?.value ?: continue
            try {
                value.fromJson(element)
            } catch (e: Exception) {
                onError("$name › ${value.name}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun resetAll() = allValues().forEach { it.reset() }
}
