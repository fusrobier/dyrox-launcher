package net.dyrox.launcher.core.instance

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import net.dyrox.shared.platform.OperatingSystem

/**
 * Windows-only control of a game's window, used when the Dyrox client mod isn't connected
 * (vanilla or other versions):
 * - [requestClose] sends WM_CLOSE, which GLFW turns into a normal "quit" (worlds get saved), unlike
 *   `Process.destroy()` which is TerminateProcess on Windows.
 * - [ensureTitleSuffix] appends "— <instance> · <account>" to the title so parallel games are distinguishable.
 * All functions are no-ops elsewhere.
 */
object WindowControl {
    private val supported = OperatingSystem.current == OperatingSystem.WINDOWS

    private interface User32Ext : StdCallLibrary {
        fun SetWindowTextW(hWnd: HWND, text: WString): Boolean
    }

    private val user32Ext: User32Ext? by lazy {
        if (supported) runCatching { Native.load("user32", User32Ext::class.java, W32APIOptions.DEFAULT_OPTIONS) }.getOrNull() else null
    }

    /**
     * Visible, titled top-level windows of [pid]. No window-class filter: the class depends on the
     * version (SDL_app for 26.x, GLFW30 for 1.13–1.21, LWJGL for legacy), and javaw has no other windows.
     */
    private fun gameWindows(pid: Long): List<HWND> {
        if (!supported) return emptyList()
        val found = ArrayList<HWND>()
        User32.INSTANCE.EnumWindows({ hwnd, _ ->
            val owner = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, owner)
            if (owner.value.toLong() == pid && User32.INSTANCE.IsWindowVisible(hwnd) && windowTitle(hwnd).isNotEmpty()) found += hwnd
            true
        }, null)
        return found
    }

    fun requestClose(pid: Long): Boolean {
        val windows = runCatching { gameWindows(pid) }.getOrDefault(emptyList())
        windows.forEach { User32.INSTANCE.PostMessage(it, WM_CLOSE, WPARAM(0), LPARAM(0)) }
        return windows.isNotEmpty()
    }

    fun ensureTitleSuffix(pid: Long, suffix: String) {
        val ext = user32Ext ?: return
        runCatching {
            for (hwnd in gameWindows(pid)) {
                val title = windowTitle(hwnd)
                if (title.isNotEmpty() && !title.endsWith(suffix)) ext.SetWindowTextW(hwnd, WString("$title$suffix"))
            }
        }
    }

    private fun windowTitle(hwnd: HWND): String {
        val buffer = CharArray(512)
        val length = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.size)
        return String(buffer, 0, length.coerceAtLeast(0))
    }

    private const val WM_CLOSE = 0x0010
}
