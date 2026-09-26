package net.dyrox.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.dyrox.launcher.LauncherInfo
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.ui.accounts.AccountDialogs
import net.dyrox.launcher.ui.accounts.AccountsScreen
import net.dyrox.launcher.ui.accounts.AccountsViewModel
import net.dyrox.launcher.ui.accounts.SecondaryButton
import net.dyrox.launcher.ui.common.VersionCatalog
import net.dyrox.launcher.ui.components.AccentButton
import net.dyrox.launcher.ui.components.AccountAvatar
import net.dyrox.launcher.ui.components.DialogActions
import net.dyrox.launcher.ui.components.ModalDialog
import net.dyrox.launcher.ui.instances.InstanceDialogs
import net.dyrox.launcher.ui.instances.InstanceTab
import net.dyrox.launcher.ui.instances.InstancesScreen
import net.dyrox.launcher.ui.instances.InstancesViewModel
import net.dyrox.launcher.ui.running.RunningScreen
import net.dyrox.launcher.ui.settings.SettingsScreen
import net.dyrox.launcher.ui.theme.DyroxColors

enum class Screen(val label: String) {
    INSTANCES("Instances"),
    RUNNING("Running"),
    ACCOUNTS("Accounts"),
    SETTINGS("Settings"),
}

/**
 * [exitRequested] is set by the window's close button while games are running;
 * the app then asks whether to leave them running or stop them first.
 */
@Composable
fun App(core: LauncherCore, exitRequested: Boolean, onCancelExit: () -> Unit, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    val accounts = remember { AccountsViewModel(core, scope) }
    val catalog = remember { VersionCatalog(core, scope) }
    val instances = remember { InstancesViewModel(core, accounts, catalog, scope) }
    var screen by remember { mutableStateOf(Screen.INSTANCES) }
    val running = instances.sessions.values.count { it.isActive }

    Box(Modifier.fillMaxSize().background(DyroxColors.Background)) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(screen, running, accounts, onSelect = { screen = it }, modifier = Modifier.width(210.dp).fillMaxHeight())
            Crossfade(screen, animationSpec = tween(180), modifier = Modifier.weight(1f)) { current ->
                when (current) {
                    Screen.INSTANCES -> InstancesScreen(instances)
                    Screen.RUNNING -> RunningScreen(instances, onOpenConsole = { id ->
                        instances.selectedId = id
                        instances.tab = InstanceTab.CONSOLE
                        screen = Screen.INSTANCES
                    })
                    Screen.ACCOUNTS -> AccountsScreen(accounts, onOpenSettings = { screen = Screen.SETTINGS })
                    Screen.SETTINGS -> SettingsScreen(core)
                }
            }
        }
        // Dialogs sit above everything, sidebar included.
        AccountDialogs(accounts, onOpenSettings = { screen = Screen.SETTINGS })
        InstanceDialogs(instances)
        ExitDialog(core, visible = exitRequested, running = running, onCancel = onCancelExit, onExit = onExit, scope = scope)
    }
}

@Composable
private fun ExitDialog(
    core: LauncherCore,
    visible: Boolean,
    running: Int,
    onCancel: () -> Unit,
    onExit: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var stopping by remember { mutableStateOf(false) }
    ModalDialog(visible = visible, title = "Games are still running", onDismiss = if (stopping) null else onCancel) {
        if (stopping) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = DyroxColors.Accent, strokeWidth = 2.dp)
                Text("Stopping $running game(s)… they get 30 seconds to save before being killed.", color = DyroxColors.TextSecondary, fontSize = 13.sp)
            }
            return@ModalDialog
        }
        Text(
            "$running game(s) are still running. If you close the launcher they keep running, but their logs, " +
                "status and in-game account switching stop until they're restarted from Dyrox.",
            color = DyroxColors.TextSecondary,
            fontSize = 13.sp,
        )
        DialogActions {
            TextButton(onClick = onCancel) { Text("Cancel", color = DyroxColors.TextSecondary) }
            SecondaryButton("Leave running", onExit)
            AccentButton("Stop all & close", {
                stopping = true
                scope.launch {
                    core.supervisor.stopAll()
                    if (!core.supervisor.awaitAllStopped(30_000)) {
                        core.supervisor.killAll()
                        core.supervisor.awaitAllStopped(5_000)
                    }
                    onExit()
                }
            })
        }
    }
}

@Composable
private fun Sidebar(current: Screen, running: Int, accounts: AccountsViewModel, onSelect: (Screen) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .background(DyroxColors.Surface)
            .border(width = 1.dp, color = DyroxColors.Border)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 18.dp)) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(DyroxColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("D", color = DyroxColors.OnAccent, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("DYROX", color = DyroxColors.TextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp)
                Text("Launcher", color = DyroxColors.TextMuted, fontSize = 11.sp)
            }
        }
        Screen.entries.forEach { screen ->
            NavItem(screen, selected = screen == current, badge = if (screen == Screen.RUNNING && running > 0) running else null) { onSelect(screen) }
        }
        Spacer(Modifier.weight(1f))
        accounts.selected?.let { account ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(Screen.ACCOUNTS) }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccountAvatar(account, accounts.core.skins, size = 28.dp)
                Column {
                    Text(account.username, color = DyroxColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Default account · ${account.type.label}", color = DyroxColors.TextMuted, fontSize = 11.sp)
                }
            }
        }
        Text("v${LauncherInfo.version}", color = DyroxColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun NavItem(screen: Screen, selected: Boolean, badge: Int?, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) DyroxColors.Accent.copy(alpha = 0.12f) else Color.Transparent, tween(150))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            screen.label,
            color = if (selected) DyroxColors.Accent else DyroxColors.TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(DyroxColors.Accent).padding(horizontal = 7.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge.toString(), color = DyroxColors.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
