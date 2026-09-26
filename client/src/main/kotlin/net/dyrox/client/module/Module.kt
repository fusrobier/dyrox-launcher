package net.dyrox.client.module

import net.dyrox.client.config.BindMode
import net.dyrox.client.config.BooleanValue
import net.dyrox.client.config.Configurable
import net.dyrox.client.config.KeyBind
import net.dyrox.client.config.KeyValue
import net.dyrox.client.event.EventBus
import net.dyrox.client.event.Events
import net.dyrox.client.event.KeyEvent
import net.dyrox.client.event.Listenable
import net.dyrox.client.event.handler
import org.slf4j.LoggerFactory

enum class Category(val displayName: String) {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    RENDER("Render"),
    WORLD("World"),
    MISC("Misc"),
    EXPLOIT("Exploit"),
    FUN("Fun"),
}

/**
 * Base of every feature. Settings are declared with the [Configurable] helpers; event handlers with
 * `handler<...> { }` and only run while the module is enabled.
 */
abstract class Module(
    name: String,
    val category: Category,
    val description: String,
    defaultKey: String? = null,
    defaultEnabled: Boolean = false,
    /** Not shown in the HUD array list. */
    val hidden: Boolean = false,
    defaultBindMode: BindMode = BindMode.TOGGLE,
) : Configurable(name), Listenable {
    private val logger = LoggerFactory.getLogger("Dyrox/Module")

    val enabledValue: BooleanValue = register(BooleanValue(ENABLED, defaultEnabled))
    val bind: KeyValue = register(KeyValue(BIND, KeyBind(defaultKey, defaultBindMode)))

    var enabled: Boolean
        get() = enabledValue.value
        set(value) {
            enabledValue.value = value
        }

    /** Extra text for the array list, e.g. the active mode. */
    open val tag: String? get() = null

    init {
        enabledValue.onChange { on ->
            // A TriggerModule only runs its action (onEnable resets it to off); it is never reported as toggled.
            if (this is TriggerModule) {
                if (on) onEnable()
                return@onChange
            }
            try {
                if (on) onEnable() else onDisable()
            } catch (e: Exception) {
                logger.error("{} failed to {}", name, if (on) "enable" else "disable", e)
            }
            toggleListeners.forEach { it(this, on) }
        }
    }

    fun toggle() {
        enabled = !enabled
    }

    override fun handleEvents(): Boolean = enabled

    protected open fun onEnable() {}

    protected open fun onDisable() {}

    override fun toString(): String = name

    companion object {
        const val ENABLED = "Enabled"
        const val BIND = "Bind"

        /** Notified on every enable/disable (notifications, array list animation). */
        val toggleListeners = java.util.concurrent.CopyOnWriteArrayList<(Module, Boolean) -> Unit>()
    }
}

/**
 * A module whose key performs an action instead of toggling a state (e.g. opening the ClickGUI).
 * It is never "enabled"; its settings still work like any other module's.
 */
abstract class TriggerModule(name: String, category: Category, description: String, defaultKey: String? = null) :
    Module(name, category, description, defaultKey, hidden = true) {
    abstract fun trigger()

    override fun onEnable() {
        // Enabling (from a command or an old config) just runs the action.
        enabled = false
        trigger()
    }
}

/** Owns all modules and turns key presses into toggles. */
class ModuleManager(bus: EventBus = Events) : Listenable {
    private val registered = ArrayList<Module>()

    val modules: List<Module> get() = registered

    init {
        handler<KeyEvent>(bus = bus) { event -> onKey(event) }
    }

    fun register(vararg modules: Module) {
        for (module in modules) {
            require(get(module.name) == null) { "Duplicate module ${module.name}" }
            registered += module
        }
        registered.sortBy { it.name.lowercase() }
    }

    /** Case- and space-insensitive lookup: "killaura" finds "Kill Aura". */
    operator fun get(name: String): Module? {
        val wanted = normalize(name)
        return registered.firstOrNull { normalize(it.name) == wanted }
    }

    fun byCategory(category: Category): List<Module> = registered.filter { it.category == category }

    private fun onKey(event: KeyEvent) {
        // Binds only fire in-game, never while typing in chat or a GUI.
        if (event.screenOpen || event.action == KeyEvent.REPEAT) return
        for (module in registered) {
            val bind = module.bind.value
            if (bind.key != event.keyName) continue
            if (module is TriggerModule) {
                if (event.action == KeyEvent.PRESS) module.trigger()
                continue
            }
            when (bind.mode) {
                BindMode.TOGGLE -> if (event.action == KeyEvent.PRESS) module.toggle()
                BindMode.HOLD -> module.enabled = event.action == KeyEvent.PRESS
            }
        }
    }

    private fun normalize(name: String) = name.replace(" ", "").lowercase()
}
