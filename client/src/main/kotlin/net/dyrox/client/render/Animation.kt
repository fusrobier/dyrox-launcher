package net.dyrox.client.render

/** Easing curves, t in 0..1. */
object Easing {
    fun linear(t: Float) = t
    fun outCubic(t: Float): Float = 1 - (1 - t).let { it * it * it }
    fun inOutCubic(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2
    fun outBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1
        val u = t - 1
        return 1 + c3 * u * u * u + c1 * u * u
    }
}

/**
 * A value that glides to its target over [durationMillis] using [easing]. Time-based, so animations
 * run at the same speed at any frame rate. Retargeting mid-animation starts from the current value.
 */
class Animated(
    initial: Float,
    var durationMillis: Float = 180f,
    private val easing: (Float) -> Float = Easing::outCubic,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var from = initial
    var target = initial
        private set
    private var startedAt = 0L

    val value: Float
        get() {
            if (durationMillis <= 0f) return target
            val t = ((clock() - startedAt) / durationMillis).coerceIn(0f, 1f)
            return from + (target - from) * easing(t)
        }

    val isDone: Boolean get() = durationMillis <= 0f || clock() - startedAt >= durationMillis

    fun animateTo(newTarget: Float) {
        if (newTarget == target) return
        from = value
        target = newTarget
        startedAt = clock()
    }

    fun snapTo(value: Float) {
        from = value
        target = value
        startedAt = 0
    }
}
