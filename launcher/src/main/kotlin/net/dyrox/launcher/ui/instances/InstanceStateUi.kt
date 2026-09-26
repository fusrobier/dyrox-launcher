package net.dyrox.launcher.ui.instances

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.core.instance.InstanceState
import net.dyrox.launcher.ui.theme.DyroxColors

fun InstanceState?.label(): String = when (this) {
    null -> "Ready"
    is InstanceState.Preparing -> stage.label
    InstanceState.Starting -> "Starting"
    InstanceState.Running -> "Running"
    InstanceState.Stopping -> "Stopping"
    is InstanceState.Exited -> if (code == 0) "Stopped" else "Stopped (exit $code)"
    is InstanceState.Crashed -> "Crashed (exit $code)"
    is InstanceState.Failed -> "Failed"
}

fun InstanceState?.color(): Color = when (this) {
    null, is InstanceState.Exited -> DyroxColors.TextMuted
    is InstanceState.Preparing, InstanceState.Starting, InstanceState.Stopping -> DyroxColors.Warning
    InstanceState.Running -> DyroxColors.Accent
    is InstanceState.Crashed, is InstanceState.Failed -> DyroxColors.Danger
}

@Composable
fun StateChip(state: InstanceState?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(state.color()))
        Text(state.label(), color = DyroxColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}

fun formatBytes(bytes: Long?): String = when {
    bytes == null -> "–"
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    else -> "${bytes / (1L shl 20)} MB"
}

fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}
