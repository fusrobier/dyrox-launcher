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
import net.minecraft.resources.Identifier
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Drawing primitives for the ClickGUI and HUD, built only on vanilla GUI pipelines (no custom shaders),
 * so they survive renderer changes. Rounded corners are anti-aliased: they are blits of a generated
 * circle texture (coverage in the alpha channel), tinted per draw, sampled with linear filtering.
 */
object Draw {
    private val CIRCLE: Identifier = Identifier.fromNamespaceAndPath(DyroxClient.MOD_ID, "ui/circle")
    private const val TEXTURE_SIZE = 128
    private const val QUADRANT = TEXTURE_SIZE / 2
    private var textureReady = false

    val font: Font get() = Minecraft.getInstance().font

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
        ensureTexture()
        // Cross made of three rectangles, plus four quarter-circle corners; nothing overlaps, so
        // translucent colours blend evenly.
        g.fill(x + r, y, x + width - r, y + height, color)
        g.fill(x, y + r, x + r, y + height - r, color)
        g.fill(x + width - r, y + r, x + width, y + height - r, color)
        corner(g, x, y, r, 0f, 0f, color)
        corner(g, x + width - r, y, r, QUADRANT.toFloat(), 0f, color)
        corner(g, x, y + height - r, r, 0f, QUADRANT.toFloat(), color)
        corner(g, x + width - r, y + height - r, r, QUADRANT.toFloat(), QUADRANT.toFloat(), color)
    }

    /** Rounded rectangle with a 1px border. [fill] should be opaque-ish, or the border shows through. */
    fun roundedOutlined(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, radius: Int, fill: Int, border: Int) {
        roundedRect(g, x, y, width, height, radius, border)
        roundedRect(g, x + 1, y + 1, width - 2, height - 2, (radius - 1).coerceAtLeast(0), fill)
    }

    fun circle(g: GuiGraphicsExtractor, centerX: Int, centerY: Int, radius: Int, color: Int) {
        roundedRect(g, centerX - radius, centerY - radius, radius * 2, radius * 2, radius, color)
    }

    fun text(g: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int, shadow: Boolean = false) {
        // Text with (near-)zero alpha is skipped by the renderer anyway; avoid the work.
        if (Colors.alpha(color) >= 4) g.text(font, text, x, y, color, shadow)
    }

    fun centeredText(g: GuiGraphicsExtractor, text: String, centerX: Int, y: Int, color: Int, shadow: Boolean = false) =
        text(g, text, centerX - width(text) / 2, y, color, shadow)

    fun width(text: String): Int = font.width(text)

    val lineHeight: Int get() = font.lineHeight

    /** Cuts [text] to fit [maxWidth], adding "…" if it had to be shortened. */
    fun ellipsize(text: String, maxWidth: Int): String {
        if (width(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && width(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun corner(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, u: Float, v: Float, color: Int) {
        g.blit(RenderPipelines.GUI_TEXTURED, CIRCLE, x, y, u, v, size, size, QUADRANT, QUADRANT, TEXTURE_SIZE, TEXTURE_SIZE, color)
    }

    /** Created on first use (the texture manager only exists once the game window is up). */
    private fun ensureTexture() {
        if (textureReady) return
        val image = NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, true)
        val center = TEXTURE_SIZE / 2f
        for (py in 0 until TEXTURE_SIZE) {
            for (px in 0 until TEXTURE_SIZE) {
                // Coverage of each texel by the disc, with a one-texel soft edge for anti-aliasing.
                val dx = px + 0.5f - center
                val dy = py + 0.5f - center
                val coverage = (center - sqrt(dx * dx + dy * dy) + 0.5f).coerceIn(0f, 1f)
                image.setPixel(px, py, ((coverage * 255).toInt() shl 24) or 0xFFFFFF)
            }
        }
        Minecraft.getInstance().textureManager.register(CIRCLE, SmoothTexture(image))
        textureReady = true
    }

    /** Like [DynamicTexture] but linearly filtered and clamped, so scaled-down corners stay smooth. */
    private class SmoothTexture(image: NativeImage) : DynamicTexture({ "dyrox:ui/circle" }, image) {
        init {
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        }
    }
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

    /** A hue cycling over time; [offset] shifts it (e.g. per array list line for a wave). */
    fun rainbow(offsetMillis: Long = 0, saturation: Float = 0.55f, brightness: Float = 1f, periodMillis: Long = 4_000): Int {
        val hue = ((System.currentTimeMillis() + offsetMillis) % periodMillis) / periodMillis.toFloat()
        return java.awt.Color.HSBtoRGB(hue, saturation, brightness) or (0xFF shl 24)
    }
}
