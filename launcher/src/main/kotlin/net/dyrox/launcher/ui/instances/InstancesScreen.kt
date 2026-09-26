package net.dyrox.launcher.ui.instances

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.dyrox.launcher.core.instance.InstanceConfig
import net.dyrox.launcher.core.instance.InstanceSession
import net.dyrox.launcher.core.instance.InstanceState
import net.dyrox.launcher.ui.accounts.SecondaryButton
import net.dyrox.launcher.ui.components.AccentButton
import net.dyrox.launcher.ui.components.AccountAvatar
import net.dyrox.launcher.ui.components.AccountTypeTag
import net.dyrox.launcher.ui.components.ConsoleView
import net.dyrox.launcher.ui.components.Panel
import net.dyrox.launcher.ui.components.SegmentedToggle
import net.dyrox.launcher.ui.theme.DyroxColors
import net.dyrox.shared.account.StoredAccount

@Composable
fun rememberSessionState(session: InstanceSession?): InstanceState? {
    val flow: StateFlow<InstanceState?> = remember(session) { session?.state ?: MutableStateFlow(null) }
    return flow.collectAsState().value
}

@Composable
fun InstancesScreen(vm: InstancesViewModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InstanceList(vm, Modifier.width(290.dp).fillMaxHeight())
        val instance = vm.selected
        if (instance == null) {
            Panel(Modifier.weight(1f)) {
                Text("No instances yet", color = DyroxColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Create one to pick a Minecraft version, loader and settings.", color = DyroxColors.TextSecondary, fontSize = 13.sp)
                AccentButton("+ New instance", { vm.dialog = InstanceDialog.NewInstance })
            }
        } else {
            InstanceDetail(vm, instance, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun InstanceList(vm: InstancesViewModel, modifier: Modifier) {
    Panel(modifier, title = "Instances", actions = {
        TextButton(onClick = { vm.dialog = InstanceDialog.NewInstance }) { Text("+ New", color = DyroxColors.Accent) }
    }) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(vm.instances, key = { it.id }) { instance ->
                InstanceRow(vm, instance, selected = instance.id == vm.selected?.id)
            }
        }
        SecondaryButton("Launch with accounts…", {
            vm.selected?.let { vm.dialog = InstanceDialog.MultiLaunch(it.linkedTo ?: it.id) }
        }, enabled = vm.selected != null && vm.accounts.accounts.isNotEmpty())
    }
}

@Composable
private fun InstanceRow(vm: InstancesViewModel, instance: InstanceConfig, selected: Boolean) {
    val state = rememberSessionState(vm.session(instance))
    val background by animateColorAsState(if (selected) DyroxColors.Accent.copy(alpha = 0.12f) else Color.Transparent, tween(150))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable { vm.select(instance) }
            .padding(start = if (instance.linkedTo != null) 22.dp else 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                (if (instance.linkedTo != null) "↳ " else "") + instance.name,
                color = if (selected) DyroxColors.Accent else DyroxColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
            )
            Text("${instance.gameVersion} · ${instance.loader.label}", color = DyroxColors.TextMuted, fontSize = 11.sp)
        }
        if (state != null) StateChip(state)
    }
}

@Composable
private fun InstanceDetail(vm: InstancesViewModel, instance: InstanceConfig, modifier: Modifier) {
    val session = vm.session(instance)
    val state = rememberSessionState(session)
    val active = state?.isActive == true
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(instance.name, color = DyroxColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${instance.gameVersion} · ${instance.loader.label} · ${instance.maxMemoryMb / 1024.0} GB" +
                        if (instance.isolatedStorage) " · isolated storage" else "",
                    color = DyroxColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
            InstanceAccountPicker(vm, instance, session?.takeIf { active }?.account)
            when (state) {
                is InstanceState.Preparing, InstanceState.Starting, InstanceState.Running ->
                    AccentButton("Stop", { vm.stop(instance) }, modifier = Modifier.width(110.dp))
                InstanceState.Stopping -> AccentButton("Kill", { vm.kill(instance) }, danger = true, modifier = Modifier.width(110.dp))
                else -> AccentButton("Play", { vm.play(instance) }, modifier = Modifier.width(110.dp), enabled = vm.accounts.accounts.isNotEmpty())
            }
            if (state == InstanceState.Running || state == InstanceState.Starting) {
                TextButton(onClick = { vm.kill(instance) }) { Text("Kill", color = DyroxColors.Danger) }
            }
        }

        StatusLine(session, state)

        AnimatedVisibility(vm.banner != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val banner = vm.banner ?: return@AnimatedVisibility
            Text(
                banner.text,
                color = if (banner.isError) DyroxColors.Danger else DyroxColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background((if (banner.isError) DyroxColors.Danger else DyroxColors.Accent).copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        SegmentedToggle(
            options = InstanceTab.entries,
            selected = vm.tab,
            label = { it.label },
            onSelect = { vm.tab = it },
            modifier = Modifier.width(260.dp),
        )
        Crossfade(vm.tab, animationSpec = tween(150), modifier = Modifier.fillMaxWidth().weight(1f)) { tab ->
            when (tab) {
                InstanceTab.CONSOLE -> ConsoleView(session?.log, Modifier.fillMaxSize())
                InstanceTab.SETTINGS -> InstanceSettingsPane(vm, instance, running = active, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StatusLine(session: InstanceSession?, state: InstanceState?) {
    val memory = session?.memoryBytes?.collectAsState()?.value
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StateChip(state)
        if (state is InstanceState.Preparing) {
            val download = state.download
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (download != null && download.totalFiles > 0) "${download.completedFiles}/${download.totalFiles} files · ${download.downloadedBytes shr 20}/${download.totalBytes shr 20} MiB" else state.detail,
                    color = DyroxColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                if (download != null && download.totalFiles > 0) {
                    LinearProgressIndicator(progress = { download.fraction }, Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)), color = DyroxColors.Accent, trackColor = DyroxColors.SurfaceHighlight)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)), color = DyroxColors.Accent, trackColor = DyroxColors.SurfaceHighlight)
                }
            }
        }
        if (state?.isActive == true && session?.pid != null) {
            Text("PID ${session.pid}", color = DyroxColors.TextMuted, fontSize = 12.sp)
            Text("RAM ${formatBytes(memory)}", color = DyroxColors.TextMuted, fontSize = 12.sp)
            if (session.ipcConnected) Text("Dyrox client connected", color = DyroxColors.Accent, fontSize = 12.sp)
        }
        if (state is InstanceState.Failed) Text(state.message, color = DyroxColors.Danger, fontSize = 12.sp, maxLines = 2, modifier = Modifier.weight(1f))
        if (state is InstanceState.Crashed && state.crashReport != null) {
            TextButton(onClick = { net.dyrox.launcher.ui.platform.DesktopActions.openFile(state.crashReport) }) {
                Text("Open crash report", color = DyroxColors.Danger)
            }
        }
    }
}

/** Which account this instance plays with: its own choice, or the launcher's selected account. */
@Composable
private fun InstanceAccountPicker(vm: InstancesViewModel, instance: InstanceConfig, playingAs: StoredAccount?) {
    var expanded by remember { mutableStateOf(false) }
    val accounts = vm.accounts.accounts
    val chosen = accounts.firstOrNull { it.id == instance.accountId }
    val shown = playingAs ?: chosen ?: vm.accounts.selected
    val shape = RoundedCornerShape(10.dp)
    Box {
        Row(
            Modifier
                .clip(shape)
                .background(DyroxColors.SurfaceElevated)
                .border(1.dp, DyroxColors.Border, shape)
                .clickable(enabled = playingAs == null) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shown != null) {
                AccountAvatar(shown, vm.core.skins, size = 24.dp)
                Column {
                    Text(shown.username, color = DyroxColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (chosen == null && playingAs == null) "Launcher default" else shown.type.label, color = DyroxColors.TextMuted, fontSize = 10.sp)
                }
            } else {
                Text("No account", color = DyroxColors.Warning, fontSize = 13.sp)
            }
            if (playingAs == null) Text("▾", color = DyroxColors.TextSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = DyroxColors.SurfaceElevated) {
            DropdownMenuItem(
                text = { Text("Launcher default" + (vm.accounts.selected?.let { " (${it.username})" } ?: ""), color = DyroxColors.TextPrimary) },
                onClick = {
                    vm.save(instance.copy(accountId = null))
                    expanded = false
                },
            )
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccountAvatar(account, vm.core.skins, size = 22.dp)
                            Text(account.username, color = DyroxColors.TextPrimary)
                            AccountTypeTag(account.type)
                        }
                    },
                    onClick = {
                        vm.save(instance.copy(accountId = account.id))
                        expanded = false
                    },
                )
            }
        }
    }
}
