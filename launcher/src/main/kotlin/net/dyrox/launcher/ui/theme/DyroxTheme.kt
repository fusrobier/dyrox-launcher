package net.dyrox.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.dyrox.shared.theme.DyroxPalette

object DyroxColors {
    val Background = Color(DyroxPalette.BACKGROUND)
    val Surface = Color(DyroxPalette.SURFACE)
    val SurfaceElevated = Color(DyroxPalette.SURFACE_ELEVATED)
    val SurfaceHighlight = Color(DyroxPalette.SURFACE_HIGHLIGHT)
    val Border = Color(DyroxPalette.BORDER)
    val Accent = Color(DyroxPalette.ACCENT)
    val AccentStrong = Color(DyroxPalette.ACCENT_STRONG)
    val OnAccent = Color(DyroxPalette.ON_ACCENT)
    val TextPrimary = Color(DyroxPalette.TEXT_PRIMARY)
    val TextSecondary = Color(DyroxPalette.TEXT_SECONDARY)
    val TextMuted = Color(DyroxPalette.TEXT_MUTED)
    val Danger = Color(DyroxPalette.DANGER)
    val Warning = Color(DyroxPalette.WARNING)
    val Info = Color(DyroxPalette.INFO)
}

private val ColorScheme = darkColorScheme(
    primary = DyroxColors.Accent,
    onPrimary = DyroxColors.OnAccent,
    primaryContainer = DyroxColors.AccentStrong,
    onPrimaryContainer = DyroxColors.OnAccent,
    secondary = DyroxColors.AccentStrong,
    onSecondary = DyroxColors.OnAccent,
    background = DyroxColors.Background,
    onBackground = DyroxColors.TextPrimary,
    surface = DyroxColors.Surface,
    onSurface = DyroxColors.TextPrimary,
    surfaceVariant = DyroxColors.SurfaceElevated,
    onSurfaceVariant = DyroxColors.TextSecondary,
    outline = DyroxColors.Border,
    outlineVariant = DyroxColors.Border,
    error = DyroxColors.Danger,
)

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun DyroxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, shapes = Shapes, content = content)
}
