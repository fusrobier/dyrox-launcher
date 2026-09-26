package net.dyrox.client.integration

import net.dyrox.shared.ipc.IpcClient
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory

/**
 * Connection to Dyrox Launcher (absent when the game was started some other way, e.g. a dev run).
 * The launcher passes the instance name and config profile as system properties and the IPC
 * port/token as environment variables.
 */
object LauncherBridge {
    private val logger = LoggerFactory.getLogger("Dyrox/Launcher")

    /** From `-Ddyrox.instance.name`; shown in the window title. */
    val instanceName: String? = System.getProperty("dyrox.instance.name")?.takeIf { it.isNotBlank() }

    /** From `-Ddyrox.profile`; the config profile this instance should use. */
    val profile: String? = System.getProperty("dyrox.profile")?.takeIf { it.isNotBlank() }

    @Volatile
    var client: IpcClient? = null
        private set

    val isConnected: Boolean get() = client?.isConnected == true

    fun connect(version: String) {
        client = IpcClient.fromEnvironment(clientVersion = "Dyrox Client $version")?.also { ipc ->
            logger.info("Connected to Dyrox Launcher")
            // "Stop" in the launcher: quit like the window's close button, so worlds are saved.
            ipc.onShutdown = {
                logger.info("Launcher requested shutdown")
                Minecraft.getInstance().execute { Minecraft.getInstance().stop() }
            }
            ipc.onDisconnected = { logger.info("Disconnected from Dyrox Launcher") }
        }
    }

    fun status(state: String, detail: String? = null) {
        client?.sendStatus(state, detail)
    }

    /** Appended to the vanilla title: "Minecraft* 26.3 — PvP #2 · Alex". */
    fun titleSuffix(username: String): String = " — ${instanceName ?: "Dyrox"} · $username"
}
