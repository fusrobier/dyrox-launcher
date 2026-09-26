package net.dyrox.shared.theme

/**
 * Colour tokens shared by the launcher (Compose) and the in-game ClickGUI/HUD, as ARGB ints,
 * so both look like one product.
 */
object DyroxPalette {
    const val BACKGROUND: Int = 0xFF0D0F12.toInt()
    const val SURFACE: Int = 0xFF15181D.toInt()
    const val SURFACE_ELEVATED: Int = 0xFF1C2027.toInt()
    const val SURFACE_HIGHLIGHT: Int = 0xFF252A33.toInt()
    const val BORDER: Int = 0xFF2A2F38.toInt()

    const val ACCENT: Int = 0xFFA3E635.toInt()
    const val ACCENT_STRONG: Int = 0xFF84CC16.toInt()
    const val ON_ACCENT: Int = 0xFF0B0D10.toInt()

    const val TEXT_PRIMARY: Int = 0xFFE8EAED.toInt()
    const val TEXT_SECONDARY: Int = 0xFF9AA0A6.toInt()
    const val TEXT_MUTED: Int = 0xFF5F6670.toInt()

    const val DANGER: Int = 0xFFEF4444.toInt()
    const val WARNING: Int = 0xFFF59E0B.toInt()
    const val INFO: Int = 0xFF38BDF8.toInt()
}
