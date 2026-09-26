package net.dyrox.launcher.ui.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.core.manifest.VersionManifest
import net.dyrox.launcher.core.process.LogLevel
import net.dyrox.launcher.core.process.LogLine
import net.dyrox.launcher.core.process.LogSource
import net.dyrox.launcher.ui.components.AccentButton
import net.dyrox.launcher.ui.components.Panel
import net.dyrox.launcher.ui.components.SegmentedToggle
import net.dyrox.launcher.ui.components.Tag
import net.dyrox.launcher.ui.components.dyroxTextFieldColors
import net.dyrox.launcher.ui.theme.DyroxColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun PlayScreen(vm: PlayViewModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        VersionPanel(vm, Modifier.width(320.dp).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LaunchPanel(vm, Modifier.fillMaxWidth())
            ConsolePanel(vm, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun VersionPanel(vm: PlayViewModel, modifier: Modifier) {
    Panel(modifier, title = "Versions") {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text("Search…", color = DyroxColors.TextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = dyroxTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = vm.showSnapshots,
                onCheckedChange = { vm.showSnapshots = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DyroxColors.Accent, checkedThumbColor = DyroxColors.OnAccent),
            )
            Spacer(Modifier.width(10.dp))
            Text("Show snapshots", color = DyroxColors.TextSecondary, fontSize = 13.sp)
        }

        val versions = vm.visibleVersions
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                vm.versions.isEmpty() && vm.loadingVersions ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = DyroxColors.Accent)
                vm.versions.isEmpty() && vm.versionsError != null ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Could not load versions", color = DyroxColors.Danger)
                        Text(vm.versionsError.orEmpty(), color = DyroxColors.TextMuted, fontSize = 12.sp)
                        TextButton(onClick = vm::refreshVersions) { Text("Retry", color = DyroxColors.Accent) }
                    }
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(versions, key = { it.id }) { entry ->
                            VersionRow(
                                entry = entry,
                                selected = entry.id == vm.selectedVersion,
                                fabric = vm.fabricVersions?.contains(entry.id) == true,
                                onClick = { vm.selectedVersion = entry.id },
                            )
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun VersionRow(entry: VersionManifest.Entry, selected: Boolean, fabric: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) DyroxColors.Accent.copy(alpha = 0.12f) else Color.Transparent, tween(150))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) DyroxColors.Accent else Color.Transparent))
        Text(
            entry.id,
            color = if (selected) DyroxColors.Accent else DyroxColors.TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (fabric) Tag("Fabric", DyroxColors.Info)
        if (!entry.isRelease) Tag(entry.type, DyroxColors.Warning)
    }
}

@Composable
private fun LaunchPanel(vm: PlayViewModel, modifier: Modifier) {
    val version = vm.selectedVersion
    Panel(modifier, title = "Launch") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Mod loader")
                SegmentedToggle(
                    options = LoaderChoice.entries,
                    selected = vm.loader,
                    label = { it.label },
                    onSelect = { vm.loader = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vm.loader == LoaderChoice.FABRIC && version != null && !vm.supportsFabric(version)) {
                    Text("Fabric doesn't support $version yet", color = DyroxColors.Warning, fontSize = 12.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("Offline username  ·  Microsoft accounts arrive in Phase 3")
                OutlinedTextField(
                    value = vm.username,
                    onValueChange = { vm.username = it.take(16) },
                    singleLine = true,
                    isError = !vm.usernameValid,
                    shape = RoundedCornerShape(10.dp),
                    colors = dyroxTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FieldLabel("Memory  ·  ${"%.1f".format(vm.maxMemoryMb / 1024f)} GB")
            Slider(
                value = vm.maxMemoryMb.toFloat(),
                onValueChange = { vm.maxMemoryMb = ((it / MEMORY_STEP).roundToInt() * MEMORY_STEP).coerceIn(MIN_MEMORY, MAX_MEMORY) },
                valueRange = MIN_MEMORY.toFloat()..MAX_MEMORY.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = DyroxColors.Accent,
                    activeTrackColor = DyroxColors.Accent,
                    inactiveTrackColor = DyroxColors.SurfaceHighlight,
                ),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { LaunchStatus(vm.state) }
            if (vm.state is LaunchUiState.Running) {
                AccentButton("Kill", onClick = vm::kill, danger = true, modifier = Modifier.width(180.dp))
            } else {
                AccentButton(
                    text = if (vm.state is LaunchUiState.Preparing) "Preparing…" else "Play ${version.orEmpty()}",
                    onClick = vm::launch,
                    enabled = vm.canLaunch,
                    modifier = Modifier.width(180.dp),
                )
            }
        }
    }
}

@Composable
private fun LaunchStatus(state: LaunchUiState) {
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
    ) { current ->
        when (current) {
            LaunchUiState.Idle -> Text("Ready", color = DyroxColors.TextSecondary, fontSize = 13.sp)
            is LaunchUiState.Preparing -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val download = current.download
                val detail = if (download != null && download.totalFiles > 0) {
                    "${download.completedFiles}/${download.totalFiles} files · ${download.downloadedBytes / MIB}/${download.totalBytes / MIB} MiB"
                } else {
                    current.detail
                }
                Text("${current.stage.label}  $detail", color = DyroxColors.TextPrimary, fontSize = 13.sp, maxLines = 1)
                if (download != null && download.totalFiles > 0) {
                    LinearProgressIndicator(
                        progress = { download.fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = DyroxColors.Accent,
                        trackColor = DyroxColors.SurfaceHighlight,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = DyroxColors.Accent,
                        trackColor = DyroxColors.SurfaceHighlight,
                    )
                }
            }
            is LaunchUiState.Running -> Text("Running  ·  PID ${current.process.pid}", color = DyroxColors.Accent, fontSize = 13.sp)
            is LaunchUiState.Exited -> Text(
                "Exited with code ${current.code}",
                color = if (current.code == 0) DyroxColors.TextSecondary else DyroxColors.Danger,
                fontSize = 13.sp,
            )
            is LaunchUiState.Failed -> Text(current.message, color = DyroxColors.Danger, fontSize = 13.sp, maxLines = 2)
        }
    }
}

@Composable
private fun ConsolePanel(vm: PlayViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.console.size, vm.followConsole) {
        if (vm.followConsole && vm.console.isNotEmpty()) listState.scrollToItem(vm.console.lastIndex)
    }
    Panel(
        modifier,
        title = "Console",
        actions = {
            Text("Follow", color = DyroxColors.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = vm.followConsole,
                onCheckedChange = { vm.followConsole = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DyroxColors.Accent, checkedThumbColor = DyroxColors.OnAccent),
            )
            TextButton(onClick = vm::clearConsole) { Text("Clear", color = DyroxColors.TextSecondary) }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(DyroxColors.Background)
                .padding(10.dp),
        ) {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = listState) {
                    itemsIndexed(vm.console) { _, line -> ConsoleLine(line) }
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
    val prefix = buildString {
        append(TIME.format(Instant.ofEpochMilli(line.timestamp)))
        append(' ')
        if (line.source == LogSource.LAUNCHER) append("[Launcher] ") else append("[${line.thread ?: line.source.name.lowercase()}/${line.level}] ")
    }
    Text(prefix + line.message, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = DyroxColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

private const val MIB = 1_048_576L
private const val MEMORY_STEP = 512
private const val MIN_MEMORY = 1024
private const val MAX_MEMORY = 16384
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
