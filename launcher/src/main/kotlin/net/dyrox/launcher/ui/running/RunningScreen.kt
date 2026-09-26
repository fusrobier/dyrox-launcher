package net.dyrox.launcher.ui.running

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.dyrox.launcher.core.instance.InstanceSession
import net.dyrox.launcher.core.instance.InstanceState
import net.dyrox.launcher.ui.accounts.SecondaryButton
import net.dyrox.launcher.ui.components.AccountAvatar
import net.dyrox.launcher.ui.components.Panel
import net.dyrox.launcher.ui.instances.InstancesViewModel
import net.dyrox.launcher.ui.instances.StateChip
import net.dyrox.launcher.ui.instances.formatBytes
import net.dyrox.launcher.ui.instances.formatDuration
import net.dyrox.launcher.ui.instances.rememberSessionState
import net.dyrox.launcher.ui.platform.DesktopActions
import net.dyrox.launcher.ui.theme.DyroxColors

/** Every running (and recently finished) game: status, account, PID, memory, uptime and controls. */
@Composable
fun RunningScreen(vm: InstancesViewModel, onOpenConsole: (String) -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val sessions = vm.sessions.values.sortedWith(compareByDescending<InstanceSession> { it.isActive }.thenBy { it.instance.name })
    val activeCount = sessions.count { it.isActive }

    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Running", color = DyroxColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("$activeCount game(s) running", color = DyroxColors.TextMuted, fontSize = 12.sp)
            }
            SecondaryButton("Stop all", { vm.core.supervisor.stopAll() }, enabled = activeCount > 0)
        }
        if (sessions.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text("Nothing running", color = DyroxColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Start instances from the Instances screen, or use \"Launch with accounts…\" to start one per account.",
                    color = DyroxColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        } else {
            HeaderRow()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.instance.id }) { session -> SessionRow(vm, session, now, onOpenConsole) }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        listOf("Instance" to 0.22f, "Account" to 0.2f, "Status" to 0.18f, "PID" to 0.1f, "Memory" to 0.1f, "Uptime" to 0.1f).forEach { (label, weight) ->
            Text(label, color = DyroxColors.TextMuted, fontSize = 11.sp, modifier = Modifier.weight(weight))
        }
        Text("", modifier = Modifier.width(250.dp))
    }
}

@Composable
private fun SessionRow(vm: InstancesViewModel, session: InstanceSession, now: Long, onOpenConsole: (String) -> Unit) {
    val state = rememberSessionState(session)
    val memory by session.memoryBytes.collectAsState()
    val shape = RoundedCornerShape(12.dp)
    val active = state?.isActive == true
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DyroxColors.Surface)
            .border(1.dp, if (state == InstanceState.Running) DyroxColors.Accent.copy(alpha = 0.5f) else DyroxColors.Border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(0.22f)) {
            Text(session.instance.name, color = DyroxColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Text("${session.instance.gameVersion} · ${session.instance.loader.label}", color = DyroxColors.TextMuted, fontSize = 11.sp)
        }
        Row(Modifier.weight(0.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountAvatar(session.account, vm.core.skins, size = 24.dp)
            Text(session.account.username, color = DyroxColors.TextPrimary, fontSize = 13.sp, maxLines = 1)
        }
        Row(Modifier.weight(0.18f)) { StateChip(state) }
        Text(session.pid?.toString() ?: "–", color = DyroxColors.TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.1f))
        Text(if (active) formatBytes(memory) else "–", color = DyroxColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(0.1f))
        Text(
            session.startedAt?.takeIf { active }?.let { formatDuration(now - it) } ?: "–",
            color = DyroxColors.TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.1f),
        )
        Row(Modifier.width(250.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onOpenConsole(session.instance.id) }) { Text("Console", color = DyroxColors.TextSecondary) }
            if (state is InstanceState.Crashed && state.crashReport != null) {
                TextButton(onClick = { DesktopActions.openFile(state.crashReport) }) { Text("Crash report", color = DyroxColors.Danger) }
            }
            if (active && state != InstanceState.Stopping) TextButton(onClick = { vm.core.supervisor.stop(session.instance.id) }) { Text("Stop", color = DyroxColors.Accent) }
            if (active) TextButton(onClick = { vm.core.supervisor.kill(session.instance.id) }) { Text("Kill", color = DyroxColors.Danger) }
        }
    }
}
