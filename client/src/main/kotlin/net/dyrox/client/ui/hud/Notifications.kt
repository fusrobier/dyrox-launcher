package net.dyrox.client.ui.hud

import net.dyrox.client.render.Animated
import net.dyrox.client.render.Colors
import net.dyrox.client.render.Draw
import net.dyrox.client.render.Easing
import net.dyrox.shared.theme.DyroxPalette
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.concurrent.CopyOnWriteArrayList

enum class NotificationType(val color: Int) {
    INFO(DyroxPalette.INFO),
    SUCCESS(DyroxPalette.ACCENT),
    WARNING(DyroxPalette.WARNING),
    ERROR(DyroxPalette.DANGER),
}

/** Toasts in the bottom-right corner: slide in, show a time bar, slide out. */
object Notifications {
    private const val WIDTH = 150
    private const val HEIGHT = 30
    private const val GAP = 4
    private const val MAX_VISIBLE = 5

    private class Toast(val title: String, val message: String, val type: NotificationType, val durationMillis: Long) {
        val createdAt = System.currentTimeMillis()
        val slide = Animated(0f, 250f, Easing::outCubic).apply { animateTo(1f) }
        val position = Animated(-1f, 200f, Easing::outCubic)
        var leaving = false

        val expired: Boolean get() = System.currentTimeMillis() - createdAt > durationMillis
    }

    private val toasts = CopyOnWriteArrayList<Toast>()

    fun show(title: String, message: String, type: NotificationType = NotificationType.INFO, durationMillis: Long = 2_500) {
        toasts += Toast(title, message, type, durationMillis)
        // Drop the oldest when too many pile up.
        while (toasts.count { !it.leaving } > MAX_VISIBLE) toasts.first { !it.leaving }.let { it.leaving = true; it.slide.animateTo(0f) }
    }

    fun render(g: GuiGraphicsExtractor) {
        val screenWidth = g.guiWidth()
        val screenHeight = g.guiHeight()
        var slot = 0
        for (toast in toasts.reversed()) {
            if (toast.expired && !toast.leaving) {
                toast.leaving = true
                toast.slide.animateTo(0f)
            }
            if (toast.leaving && toast.slide.isDone && toast.slide.value <= 0.01f) {
                toasts.remove(toast)
                continue
            }
            // Each toast glides to its slot when others above it disappear.
            if (toast.position.value < 0) toast.position.snapTo(slot.toFloat()) else toast.position.animateTo(slot.toFloat())
            val progress = toast.slide.value
            val x = screenWidth - ((WIDTH + 6) * progress).toInt()
            val y = screenHeight - 6 - HEIGHT - (toast.position.value * (HEIGHT + GAP)).toInt()
            val alpha = progress

            Draw.roundedOutlined(g, x, y, WIDTH, HEIGHT, 5, Colors.fade(DyroxPalette.SURFACE, alpha * 0.95f), Colors.fade(DyroxPalette.BORDER, alpha))
            Draw.roundedRect(g, x + 4, y + 6, 3, HEIGHT - 12, 1, Colors.fade(toast.type.color, alpha))
            Draw.text(g, Draw.ellipsize(toast.title, WIDTH - 20), x + 12, y + 5, Colors.fade(DyroxPalette.TEXT_PRIMARY, alpha))
            Draw.text(g, Draw.ellipsize(toast.message, WIDTH - 20), x + 12, y + 16, Colors.fade(DyroxPalette.TEXT_SECONDARY, alpha))
            // Remaining-time bar along the bottom edge.
            val remaining = 1f - ((System.currentTimeMillis() - toast.createdAt) / toast.durationMillis.toFloat()).coerceIn(0f, 1f)
            Draw.rect(g, x + 5, y + HEIGHT - 3, ((WIDTH - 10) * remaining).toInt(), 1, Colors.fade(toast.type.color, alpha * 0.8f))
            slot++
        }
    }
}
