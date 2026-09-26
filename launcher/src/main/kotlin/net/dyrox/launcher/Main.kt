package net.dyrox.launcher

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.ui.App
import net.dyrox.launcher.ui.theme.DyroxTheme
import java.awt.Dimension

fun main() {
    val core = LauncherCore()
    application {
        val windowState = rememberWindowState(width = 1200.dp, height = 760.dp, position = WindowPosition(Alignment.Center))
        Window(onCloseRequest = ::exitApplication, title = LauncherInfo.DISPLAY_NAME, state = windowState) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(980, 620) }
            DyroxTheme { App(core) }
        }
    }
}
