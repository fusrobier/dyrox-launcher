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
import androidx.compose.material3.Text
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
import net.dyrox.launcher.LauncherInfo
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.ui.accounts.AccountDialogs
import net.dyrox.launcher.ui.accounts.AccountsScreen
import net.dyrox.launcher.ui.accounts.AccountsViewModel
import net.dyrox.launcher.ui.components.AccountAvatar
import net.dyrox.launcher.ui.play.PlayScreen
import net.dyrox.launcher.ui.play.PlayViewModel
import net.dyrox.launcher.ui.settings.SettingsScreen
import net.dyrox.launcher.ui.theme.DyroxColors

enum class Screen(val label: String, val hint: String? = null) {
    PLAY("Play"),
    ACCOUNTS("Accounts"),
    INSTANCES("Instances", hint = "Phase 4"),
    SETTINGS("Settings"),
}

@Composable
fun App(core: LauncherCore) {
    val scope = rememberCoroutineScope()
    val accounts = remember { AccountsViewModel(core, scope) }
    val play = remember { PlayViewModel(core, accounts, scope) }
    var screen by remember { mutableStateOf(Screen.PLAY) }

    Box(Modifier.fillMaxSize().background(DyroxColors.Background)) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(screen, accounts, onSelect = { screen = it }, modifier = Modifier.width(210.dp).fillMaxHeight())
            Crossfade(screen, animationSpec = tween(180), modifier = Modifier.weight(1f)) { current ->
                when (current) {
                    Screen.PLAY -> PlayScreen(play, accounts, onManageAccounts = { screen = Screen.ACCOUNTS })
                    Screen.ACCOUNTS -> AccountsScreen(accounts, onOpenSettings = { screen = Screen.SETTINGS })
                    Screen.SETTINGS -> SettingsScreen(core)
                    Screen.INSTANCES -> Unit
                }
            }
        }
        // Dialogs sit above everything, sidebar included.
        AccountDialogs(accounts, onOpenSettings = { screen = Screen.SETTINGS })
    }
}

@Composable
private fun Sidebar(current: Screen, accounts: AccountsViewModel, onSelect: (Screen) -> Unit, modifier: Modifier) {
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
            NavItem(screen, selected = screen == current, onClick = { onSelect(screen) })
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
                    Text(account.type.label, color = DyroxColors.TextMuted, fontSize = 11.sp)
                }
            }
        }
        Text("v${LauncherInfo.version}", color = DyroxColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun NavItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    val enabled = screen.hint == null
    val background by animateColorAsState(if (selected) DyroxColors.Accent.copy(alpha = 0.12f) else Color.Transparent, tween(150))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            screen.label,
            color = when {
                selected -> DyroxColors.Accent
                !enabled -> DyroxColors.TextMuted
                else -> DyroxColors.TextSecondary
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        screen.hint?.let { Text(it, color = DyroxColors.TextMuted, fontSize = 10.sp) }
    }
}
