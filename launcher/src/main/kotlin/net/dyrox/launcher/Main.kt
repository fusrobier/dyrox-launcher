package net.dyrox.launcher

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.ui.App
import net.dyrox.launcher.ui.theme.DyroxTheme
import org.jetbrains.skia.Image
import java.awt.Dimension

fun main() {
    val core = LauncherCore()
    val icon = LauncherCore::class.java.getResourceAsStream("/dyrox-icon.png")?.use { BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()) }
    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp, position = WindowPosition(Alignment.Center))
        var exitRequested by remember { mutableStateOf(false) }
        val exit = {
            core.supervisor.close()
            exitApplication()
        }
        Window(
            onCloseRequest = { if (core.supervisor.activeSessions.isEmpty()) exit() else exitRequested = true },
            title = LauncherInfo.DISPLAY_NAME,
            icon = icon,
            state = windowState,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(1080, 680) }
            DyroxTheme {
                App(core, exitRequested, onCancelExit = { exitRequested = false }, onExit = exit)
            }
        }
    }
}
