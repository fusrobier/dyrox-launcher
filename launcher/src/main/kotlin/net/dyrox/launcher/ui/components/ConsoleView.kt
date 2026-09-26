package net.dyrox.launcher.ui.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.dyrox.launcher.core.instance.LogBuffer
import net.dyrox.launcher.core.process.LogLevel
import net.dyrox.launcher.core.process.LogLine
import net.dyrox.launcher.core.process.LogSource
import net.dyrox.launcher.ui.platform.DesktopActions
import net.dyrox.launcher.ui.theme.DyroxColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Live view of one instance's [LogBuffer]: polls for new entries ten times a second (only the new ones
 * are copied), colours by level, and can filter, follow the tail and copy everything.
 */
@Composable
fun ConsoleView(buffer: LogBuffer?, modifier: Modifier = Modifier) {
    val lines = remember(buffer) { mutableStateListOf<LogLine>() }
    var follow by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(buffer) {
        if (buffer == null) return@LaunchedEffect
        var lastSeq = -1L
        while (true) {
            val fresh = buffer.after(lastSeq)
            if (fresh.isNotEmpty()) {
                lastSeq = fresh.last().seq
                lines.addAll(fresh.map { it.line })
                val overflow = lines.size - MAX_LINES
                if (overflow > 0) lines.removeRange(0, overflow)
            }
            delay(100)
        }
    }
    val visible = if (filter.isBlank()) lines else lines.filter { it.message.contains(filter, ignoreCase = true) }
    LaunchedEffect(visible.size, follow) {
        if (follow && visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Filter log…", color = DyroxColors.TextMuted, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = dyroxTextFieldColors(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text("Follow", color = DyroxColors.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = follow,
                onCheckedChange = { follow = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DyroxColors.Accent, checkedThumbColor = DyroxColors.OnAccent),
            )
            TextButton(onClick = { DesktopActions.copyToClipboard(lines.joinToString("\n", transform = ::format)) }) {
                Text("Copy", color = DyroxColors.TextSecondary)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(DyroxColors.Background)
                .padding(10.dp),
        ) {
            if (buffer == null || lines.isEmpty()) {
                Text(
                    if (buffer == null) "Not started yet. Press Play." else "Waiting for output…",
                    color = DyroxColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = listState) {
                    items(visible) { line -> ConsoleLine(line) }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun ConsoleLine(line: LogLine) {
    val color = when {
        line.source == LogSource.LAUNCHER && line.level < LogLevel.WARN -> DyroxColors.Accent
        line.level >= LogLevel.ERROR -> DyroxColors.Danger
        line.level == LogLevel.WARN -> DyroxColors.Warning
        line.level <= LogLevel.DEBUG -> DyroxColors.TextMuted
        else -> DyroxColors.TextPrimary
    }
    Text(format(line), color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth())
}

private fun format(line: LogLine): String {
    val source = if (line.source == LogSource.LAUNCHER) "[Launcher]" else "[${line.thread ?: line.source.name.lowercase()}/${line.level}]"
    return "${TIME.format(Instant.ofEpochMilli(line.timestamp))} $source ${line.message}"
}

private const val MAX_LINES = 20_000
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
