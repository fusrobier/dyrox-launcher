package net.dyrox.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.LauncherInfo
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.ui.play.PlayScreen
import net.dyrox.launcher.ui.play.PlayViewModel
import net.dyrox.launcher.ui.theme.DyroxColors

@Composable
fun App(core: LauncherCore) {
    val scope = rememberCoroutineScope()
    val playViewModel = remember { PlayViewModel(core, scope) }
    Row(Modifier.fillMaxSize().background(DyroxColors.Background)) {
        Sidebar(Modifier.width(210.dp).fillMaxHeight())
        PlayScreen(playViewModel, Modifier.weight(1f))
    }
}

@Composable
private fun Sidebar(modifier: Modifier) {
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
        NavItem("Play", selected = true)
        NavItem("Accounts", hint = "Phase 3")
        NavItem("Instances", hint = "Phase 4")
        NavItem("Settings", hint = "Phase 8")
        Spacer(Modifier.weight(1f))
        Text("v${LauncherInfo.version}", color = DyroxColors.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean = false, hint: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) DyroxColors.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = when {
                selected -> DyroxColors.Accent
                hint != null -> DyroxColors.TextMuted
                else -> DyroxColors.TextSecondary
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) Text(hint, color = DyroxColors.TextMuted, fontSize = 10.sp)
    }
}
