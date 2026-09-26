package net.dyrox.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.ui.theme.DyroxColors

/**
 * A modal card over a dimmed backdrop, animated in and out. Clicking the backdrop calls [onDismiss]
 * (pass null to make the dialog non-dismissable, e.g. while a sign-in is running).
 */
@Composable
fun ModalDialog(
    visible: Boolean,
    title: String,
    onDismiss: (() -> Unit)?,
    width: Dp = 460.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(visible, enter = scaleIn(tween(180), initialScale = 0.94f), exit = scaleOut(tween(120), targetScale = 0.96f)) {
                Column(
                    Modifier
                        .widthIn(max = width)
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DyroxColors.Surface)
                        .border(1.dp, DyroxColors.Border, RoundedCornerShape(16.dp))
                        // Swallow clicks so they don't reach the backdrop.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(title, color = DyroxColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    content()
                }
            }
        }
    }
}

/** Right-aligned row of dialog buttons. */
@Composable
fun DialogActions(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}
