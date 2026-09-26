package net.dyrox.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.ui.theme.DyroxColors

private val PanelShape = RoundedCornerShape(14.dp)

/** Rounded card with an optional title row; the basic building block of every screen. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(PanelShape)
            .background(DyroxColors.Surface)
            .border(1.dp, DyroxColors.Border, PanelShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DyroxColors.TextPrimary, modifier = Modifier.weight(1f))
                actions()
            }
        }
        content()
    }
}

/** The primary lime call-to-action button. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) DyroxColors.Danger else DyroxColors.Accent,
            contentColor = if (danger) Color.White else DyroxColors.OnAccent,
            disabledContainerColor = DyroxColors.SurfaceHighlight,
            disabledContentColor = DyroxColors.TextMuted,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Pill-shaped single-choice toggle with an animated selection colour. */
@Composable
fun <T> SegmentedToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: (T) -> Boolean = { true },
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DyroxColors.SurfaceElevated)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val enabled = isEnabled(option)
            val background by animateColorAsState(if (isSelected) DyroxColors.Accent else Color.Transparent, tween(180))
            val foreground by animateColorAsState(
                when {
                    isSelected -> DyroxColors.OnAccent
                    enabled -> DyroxColors.TextSecondary
                    else -> DyroxColors.TextMuted
                },
                tween(180),
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .clickable(enabled = enabled) { onSelect(option) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun dyroxTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DyroxColors.Accent,
    unfocusedBorderColor = DyroxColors.Border,
    cursorColor = DyroxColors.Accent,
    focusedLabelColor = DyroxColors.Accent,
    unfocusedLabelColor = DyroxColors.TextSecondary,
    focusedContainerColor = DyroxColors.SurfaceElevated,
    unfocusedContainerColor = DyroxColors.SurfaceElevated,
)
