package net.dyrox.client.event

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface Event

/** An event a handler can veto, e.g. an outgoing packet. */
abstract class CancellableEvent : Event {
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }
}

/**
 * Something that owns event handlers. Handlers only run while [isRunning]: the owner and all its
 * parents allow it (a module's mode only runs while the module is enabled and that mode is selected).
 */
interface Listenable {
    fun handleEvents(): Boolean = true

    val parentListenable: Listenable? get() = null

    fun isRunning(): Boolean = handleEvents() && (parentListenable?.isRunning() ?: true)
}

class EventHook<T : Event>(
    val owner: Listenable,
    val priority: Int,
    /** Runs even while the owner is disabled (e.g. keybind handling). */
    val ignoreCondition: Boolean,
    val handler: (T) -> Unit,
)

/**
 * Typed publish/subscribe with priorities. Lookup is by exact event class, so posting costs one map
 * lookup and a list walk. Exceptions in a handler are logged, never rethrown: a buggy module must not
 * crash the game or starve other handlers.
 */
class EventBus {
    private val hooks = ConcurrentHashMap<Class<out Event>, CopyOnWriteArrayList<EventHook<out Event>>>()
    private val logger = LoggerFactory.getLogger("Dyrox/Events")
    private val reportedFailures = ConcurrentHashMap.newKeySet<String>()

    fun <T : Event> register(type: Class<T>, hook: EventHook<T>): EventHook<T> {
        val list = hooks.computeIfAbsent(type) { CopyOnWriteArrayList() }
        synchronized(list) {
            // Keep the list sorted by priority (highest first); equal priorities keep registration order.
            val index = list.indexOfFirst { it.priority < hook.priority }.let { if (it == -1) list.size else it }
            list.add(index, hook)
        }
        return hook
    }

    fun unregister(hook: EventHook<*>) {
        hooks.values.forEach { it.remove(hook) }
    }

    fun unregisterAll(owner: Listenable) {
        hooks.values.forEach { list -> list.removeIf { it.owner === owner } }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Event> post(event: T): T {
        val list = hooks[event.javaClass] ?: return event
        for (hook in list) {
            hook as EventHook<T>
            if (!hook.ignoreCondition && !hook.owner.isRunning()) continue
            try {
                hook.handler(event)
            } catch (e: Throwable) {
                // Log each failing handler once instead of flooding the log every tick.
                val key = "${hook.owner.javaClass.name}/${event.javaClass.simpleName}"
                if (reportedFailures.add(key)) logger.error("Handler of {} failed on {}", hook.owner, event.javaClass.simpleName, e)
            }
        }
        return event
    }

    fun handlerCount(type: Class<out Event>): Int = hooks[type]?.size ?: 0
}

/** The client-wide bus used by modules and mixins. */
val Events = EventBus()

/**
 * LiquidBounce-style handler declaration:
 * ```
 * val onTick = handler<GameTickEvent> { ... }
 * ```
 */
inline fun <reified T : Event> Listenable.handler(
    priority: Int = 0,
    ignoreCondition: Boolean = false,
    bus: EventBus = Events,
    noinline handler: (T) -> Unit,
): EventHook<T> = bus.register(T::class.java, EventHook(this, priority, ignoreCondition, handler))
