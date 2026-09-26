package net.dyrox.launcher.ui.platform

import net.dyrox.shared.platform.OperatingSystem
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.nio.file.Path

/** Small wrappers around AWT desktop integration, with Linux fallbacks. */
object DesktopActions {
    fun openUrl(url: String): Boolean = try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            true
        } else {
            xdgOpen(url)
        }
    } catch (_: Exception) {
        xdgOpen(url)
    }

    fun openFolder(path: Path): Boolean = try {
        path.toFile().mkdirs()
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(path.toFile())
            true
        } else {
            xdgOpen(path.toString())
        }
    } catch (_: Exception) {
        xdgOpen(path.toString())
    }

    /** Opens a file (e.g. a crash report) with its default application. */
    fun openFile(path: Path): Boolean = try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(path.toFile())
            true
        } else {
            xdgOpen(path.toString())
        }
    } catch (_: Exception) {
        xdgOpen(path.toString())
    }

    /** Native folder picker (Swing, since AWT's FileDialog can't pick folders on Windows). */
    fun chooseFolder(title: String): Path? {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
    }

    fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    /** Native save dialog; returns null if cancelled. */
    fun chooseSaveFile(title: String, defaultName: String): Path? = fileDialog(title, FileDialog.SAVE, defaultName)

    /** Native open dialog; returns null if cancelled. */
    fun chooseOpenFile(title: String): Path? = fileDialog(title, FileDialog.LOAD, null)

    private fun fileDialog(title: String, mode: Int, defaultName: String?): Path? {
        val dialog = FileDialog(null as Frame?, title, mode)
        defaultName?.let { dialog.file = it }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return Path.of(dialog.directory, file)
    }

    private fun xdgOpen(target: String): Boolean {
        if (OperatingSystem.current != OperatingSystem.LINUX) return false
        return runCatching { ProcessBuilder("xdg-open", target).start() }.isSuccess
    }
}
