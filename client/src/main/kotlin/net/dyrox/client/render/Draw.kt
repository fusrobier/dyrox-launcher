package net.dyrox.client.render

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import net.dyrox.client.DyroxClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Which typeface Dyrox UI text uses. */
enum class UiFont { INTER, MINECRAFT }

/**
 * Drawing primitives for the ClickGUI and HUD, built only on vanilla GUI pipelines (no custom shaders),
 * so they survive renderer changes. Rounded shapes are anti-aliased: corners are blits of generated
 * coverage textures (a disc for fills, a ring per radius for 1px rims), tinted per draw and sampled
 * with linear filtering.
 */
object Draw {
    private const val DISC_SIZE = 128
    private const val DISC_QUADRANT = DISC_SIZE / 2
    /** Rings are rendered this many times larger than drawn, then filtered down. */
    private const val RING_SUPERSAMPLE = 8

    private val DISC: Identifier = id("ui/disc")
    private var discReady = false
    private val rings = HashMap<Int, Identifier>()

    /** Inter (SIL OFL), closest open match to Apple's SF Pro; SF Pro itself may not be redistributed. */
    private val INTER = FontDescription.Resource(id("glass"))
    private val INTER_BOLD = FontDescription.Resource(id("glass_bold"))

    /** Set from the ClickGUI "Font" setting. */
    var uiFont: UiFont = UiFont.INTER

    val font: Font get() = Minecraft.getInstance().font

    private fun id(path: String) = Identifier.fromNamespaceAndPath(DyroxClient.MOD_ID, path)

    // --- Shapes ---

    fun rect(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
        if (width > 0 && height > 0 && Colors.alpha(color) > 0) g.fill(x, y, x + width, y + height, color)
    }

    fun gradientV(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, top: Int, bottom: Int) {
        if (width > 0 && height > 0) g.fillGradient(x, y, x + width, y + height, top, bottom)
    }

    fun roundedRect(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, color: Int) {
        if (width <= 0 || height <= 0 || Colors.alpha(color) == 0) return
        val r = min(radius, min(width, height) / 2)
        if (r <= 0) {
            rect(g, x, y, width, height, color)
            return
        }
        ensureDisc()
        // A cross of three rectangles plus four quarter-disc corners; nothing overlaps, so translucent
        // colours blend evenly (essential for glass).
        g.fill(x + r, y, x + width - r, y + height, color)
        g.fill(x, y + r, x + r, y + height - r, color)
        g.fill(x + width - r, y + r, x + width, y + height - r, color)
        val q = DISC_QUADRANT.toFloat()
        disc(g, x, y, r, 0f, 0f, color)
        disc(g, x + width - r, y, r, q, 0f, color)
        disc(g, x, y + height - r, r, 0f, q, color)
        disc(g, x + width - r, y + height - r, r, q, q, color)
    }

    /** A 1px anti-aliased outline of a rounded rectangle, without filling the inside. */
    fun roundedRing(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, color: Int) {
        if (width <= 1 || height <= 1 || Colors.alpha(color) == 0) return
        val r = min(radius, min(width, height) / 2)
        if (r <= 1) {
            rect(g, x, y, width, 1, color)
            rect(g, x, y + height - 1, width, 1, color)
            rect(g, x, y + 1, 1, height - 2, color)
            rect(g, x + width - 1, y + 1, 1, height - 2, color)
            return
        }
        rect(g, x + r, y, width - 2 * r, 1, color)
        rect(g, x + r, y + height - 1, width - 2 * r, 1, color)
        rect(g, x, y + r, 1, height - 2 * r, color)
        rect(g, x + width - 1, y + r, 1, height - 2 * r, color)
        val texture = ring(r)
        val quadrant = r * RING_SUPERSAMPLE
        val size = quadrant * 2
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, r, r, quadrant, quadrant, size, size, color)
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x + width - r, y, quadrant.toFloat(), 0f, r, r, quadrant, quadrant, size, size, color)
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y + height - r, 0f, quadrant.toFloat(), r, r, quadrant, quadrant, size, size, color)
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x + width - r, y + height - r, quadrant.toFloat(), quadrant.toFloat(), r, r, quadrant, quadrant, size, size, color)
    }

    fun circle(g: GuiGraphicsExtractor, centerX: Int, centerY: Int, radius: Int, color: Int) =
        roundedRect(g, centerX - radius, centerY - radius, radius * 2, radius * 2, radius, color)

    /** Soft drop shadow: stacked, growing, fading rounded rectangles. */
    fun shadow(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, strength: Float = 1f, offsetY: Int = 3) {
        for (i in 1..5) {
            val alpha = ((18 - i * 3) * strength).roundToInt()
            if (alpha <= 0) continue
            roundedRect(g, x - i, y - i + offsetY, width + 2 * i, height + 2 * i, radius + i, alpha shl 24)
        }
    }

    /**
     * A Liquid Glass surface: translucent frosted body (the vanilla blur behind the screen does the
     * frosting), a sheen fading down from the top, a bright top edge and a thin light rim.
     */
    fun glass(
        g: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radius: Int,
        tint: Int = Glass.BODY,
        shadow: Boolean = true,
        alpha: Float = 1f,
    ) {
        if (shadow) shadow(g, x, y, width, height, radius, alpha)
        roundedRect(g, x, y, width, height, radius, Colors.fade(tint, alpha))
        val r = min(radius, min(width, height) / 2)
        // Sheen: brighter near the top, inset so it never spills past the rounded corners.
        val sheenHeight = min(height / 2, 16)
        gradientV(g, x + r, y + 1, width - 2 * r, sheenHeight, Colors.fade(Glass.SHEEN, alpha), 0)
        roundedRing(g, x, y, width, height, radius, Colors.fade(Glass.RIM, alpha))
        // Specular highlight along the top edge.
        rect(g, x + r, y, width - 2 * r, 1, Colors.fade(Glass.HIGHLIGHT, alpha))
    }

    // --- Text ---

    private fun styled(text: String, bold: Boolean): Component {
        val component = Component.literal(text)
        return if (uiFont == UiFont.INTER) component.withStyle(Style.EMPTY.withFont(if (bold) INTER_BOLD else INTER)) else component
    }

    fun text(g: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int, shadow: Boolean = false, bold: Boolean = false) {
        // Text with (near-)zero alpha is skipped by the renderer anyway; avoid the work.
        if (Colors.alpha(color) >= 4 && text.isNotEmpty()) g.text(font, styled(text, bold), x, y, color, shadow)
    }

    fun centeredText(g: GuiGraphicsExtractor, text: String, centerX: Int, y: Int, color: Int, shadow: Boolean = false, bold: Boolean = false) =
        text(g, text, centerX - width(text, bold) / 2, y, color, shadow, bold)

    fun width(text: String, bold: Boolean = false): Int = font.width(styled(text, bold))

    val lineHeight: Int get() = font.lineHeight

    /** Cuts [text] to fit [maxWidth], adding "…" if it had to be shortened. */
    fun ellipsize(text: String, maxWidth: Int, bold: Boolean = false): String {
        if (width(text, bold) <= maxWidth) return text
        var end = text.length
        while (end > 0 && width(text.substring(0, end) + "…", bold) > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    // --- Textures ---

    private fun disc(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, u: Float, v: Float, color: Int) {
        g.blit(RenderPipelines.GUI_TEXTURED, DISC, x, y, u, v, size, size, DISC_QUADRANT, DISC_QUADRANT, DISC_SIZE, DISC_SIZE, color)
    }

    /** Created on first use (the texture manager only exists once the game window is up). */
    private fun ensureDisc() {
        if (discReady) return
        register(DISC, coverageImage(DISC_SIZE) { d -> (DISC_SIZE / 2f - d + 0.5f).coerceIn(0f, 1f) })
        discReady = true
    }

    /** Ring corner texture for [radius]: a 1-GUI-pixel band at the edge of a disc, supersampled. */
    private fun ring(radius: Int): Identifier = rings.getOrPut(radius) {
        val size = radius * 2 * RING_SUPERSAMPLE
        val outer = size / 2f
        val inner = outer - RING_SUPERSAMPLE
        val id = id("ui/ring_$radius")
        register(id, coverageImage(size) { d -> (outer - d + 0.5f).coerceIn(0f, 1f) - (inner - d + 0.5f).coerceIn(0f, 1f) })
        id
    }

    private inline fun coverageImage(size: Int, coverage: (distanceFromCenter: Float) -> Float): NativeImage {
        val image = NativeImage(size, size, true)
        val center = size / 2f
        for (py in 0 until size) {
            for (px in 0 until size) {
                val dx = px + 0.5f - center
                val dy = py + 0.5f - center
                val a = coverage(sqrt(dx * dx + dy * dy)).coerceIn(0f, 1f)
                image.setPixel(px, py, ((a * 255).toInt() shl 24) or 0xFFFFFF)
            }
        }
        return image
    }

    private fun register(id: Identifier, image: NativeImage) {
        Minecraft.getInstance().textureManager.register(id, SmoothTexture(id.toString(), image))
    }

    /** Like [DynamicTexture] but linearly filtered and clamped, so scaled-down corners stay smooth. */
    private class SmoothTexture(label: String, image: NativeImage) : DynamicTexture({ label }, image) {
        init {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        }
    }
}

/** Liquid Glass colour tokens (ARGB). White at low opacity over the blurred scene reads as frosted glass. */
object Glass {
    const val BODY = 0x2EFFFFFF
    const val BODY_STRONG = 0x42FFFFFF
    const val SHEEN = 0x1CFFFFFF
    const val RIM = 0x59FFFFFF
    const val HIGHLIGHT = 0x9EFFFFFF.toInt()
    const val HOVER = 0x1AFFFFFF
    const val DIVIDER = 0x26FFFFFF
    const val TRACK = 0x33FFFFFF
    const val KNOB = 0xFFFFFFFF.toInt()
    const val TEXT = 0xF5FFFFFF.toInt()
    const val TEXT_DIM = 0xB8FFFFFF.toInt()
    const val TEXT_MUTED = 0x80FFFFFF.toInt()
    /** Darkening over the blurred world behind the menu, so white text stays readable on bright scenes. */
    const val BACKDROP = 0x66000000
    /** HUD surfaces sit over the unblurred game, so they use a darker tint to stay readable. */
    const val HUD = 0xA6101318.toInt()
}

object Colors {
    fun alpha(argb: Int): Int = argb ushr 24

    fun withAlpha(argb: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (argb and 0xFFFFFF)

    /** Multiplies the colour's alpha by [factor] (0..1); used for fades. */
    fun fade(argb: Int, factor: Float): Int = withAlpha(argb, (alpha(argb) * factor.coerceIn(0f, 1f)).toInt())

    fun lerp(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun channel(shift: Int) = ((from shr shift and 0xFF) + ((to shr shift and 0xFF) - (from shr shift and 0xFF)) * k).toInt() and 0xFF
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** A hue cycling over time; [offsetMillis] shifts it (e.g. per array list line for a wave). */
    fun rainbow(offsetMillis: Long = 0, saturation: Float = 0.55f, brightness: Float = 1f, periodMillis: Long = 4_000): Int {
        val hue = ((System.currentTimeMillis() + offsetMillis) % periodMillis) / periodMillis.toFloat()
        return java.awt.Color.HSBtoRGB(hue, saturation, brightness) or (0xFF shl 24)
    }
}
