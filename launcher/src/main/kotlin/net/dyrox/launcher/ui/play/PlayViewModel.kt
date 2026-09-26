package net.dyrox.launcher.ui.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.dyrox.launcher.core.LaunchProgress
import net.dyrox.launcher.core.LaunchRequest
import net.dyrox.launcher.core.LaunchStage
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.core.LoaderSpec
import net.dyrox.launcher.core.download.DownloadProgress
import net.dyrox.launcher.core.launch.LaunchIdentity
import net.dyrox.launcher.core.manifest.VersionManifest
import net.dyrox.launcher.core.process.GameProcess
import net.dyrox.launcher.core.process.LogLevel
import net.dyrox.launcher.core.process.LogLine
import net.dyrox.launcher.core.process.LogSource
import net.dyrox.shared.auth.OfflineProfiles
import java.util.concurrent.ConcurrentLinkedQueue

enum class LoaderChoice(val label: String) { VANILLA("Vanilla"), FABRIC("Fabric") }

sealed interface LaunchUiState {
    data object Idle : LaunchUiState
    data class Preparing(val stage: LaunchStage, val detail: String, val download: DownloadProgress?) : LaunchUiState
    data class Running(val process: GameProcess) : LaunchUiState
    data class Exited(val code: Int) : LaunchUiState
    data class Failed(val message: String) : LaunchUiState
}

/** State and actions for the Play screen. All state is read and written on the UI thread. */
class PlayViewModel(private val core: LauncherCore, private val scope: CoroutineScope) {
    var versions by mutableStateOf<List<VersionManifest.Entry>>(emptyList()); private set
    var loadingVersions by mutableStateOf(false); private set
    var versionsError by mutableStateOf<String?>(null); private set
    /** Game versions Fabric supports; null until known (or if Fabric's API is unreachable). */
    var fabricVersions by mutableStateOf<Set<String>?>(null); private set

    var query by mutableStateOf("")
    var showSnapshots by mutableStateOf(false)
    var selectedVersion by mutableStateOf<String?>(null)
    var loader by mutableStateOf(LoaderChoice.FABRIC)
    var username by mutableStateOf("Player")
    var maxMemoryMb by mutableStateOf(4096)
    var followConsole by mutableStateOf(true)

    var state by mutableStateOf<LaunchUiState>(LaunchUiState.Idle); private set
    val console = mutableStateListOf<LogLine>()

    /** Game output arrives on I/O threads; it's batched here and flushed to [console] ten times a second. */
    private val incoming = ConcurrentLinkedQueue<LogLine>()

    val visibleVersions: List<VersionManifest.Entry>
        get() {
            val needle = query.trim()
            return versions.filter { (it.isRelease || (showSnapshots && it.isSnapshot)) && it.id.contains(needle, ignoreCase = true) }
        }

    fun supportsFabric(version: String): Boolean = fabricVersions?.contains(version) ?: true

    val usernameValid: Boolean get() = OfflineProfiles.isValidName(username)
    val isBusy: Boolean get() = state is LaunchUiState.Preparing || state is LaunchUiState.Running

    val canLaunch: Boolean
        get() {
            val version = selectedVersion ?: return false
            return usernameValid && !isBusy && (loader == LoaderChoice.VANILLA || supportsFabric(version))
        }

    init {
        refreshVersions()
        scope.launch {
            while (isActive) {
                flushConsole()
                delay(100)
            }
        }
    }

    fun refreshVersions() {
        scope.launch {
            loadingVersions = true
            try {
                val manifest = core.manifests.manifest()
                versions = manifest.versions
                if (selectedVersion == null) selectedVersion = manifest.latest.release
                versionsError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                versionsError = e.message ?: e.toString()
            } finally {
                loadingVersions = false
            }
        }
        scope.launch {
            fabricVersions = runCatching { core.fabric.supportedGameVersions().map { it.version }.toSet() }.getOrNull()
        }
    }

    fun launch() {
        val version = selectedVersion ?: return
        if (!canLaunch) return
        val instance = core.defaultInstanceDir
        val request = LaunchRequest(
            gameVersion = version,
            loader = if (loader == LoaderChoice.FABRIC) LoaderSpec.Fabric() else LoaderSpec.Vanilla,
            identity = LaunchIdentity.offline(username),
            gameDirectory = instance.resolve("minecraft"),
            nativesDirectory = instance.resolve("natives"),
            minMemoryMb = minOf(512, maxMemoryMb),
            maxMemoryMb = maxMemoryMb,
            systemProperties = mapOf("dyrox.instance" to "default"),
        )
        state = LaunchUiState.Preparing(LaunchStage.RESOLVING, version, null)
        log("Launching $version (${loader.label}) as $username [offline]")

        scope.launch {
            try {
                val command = core.gameLauncher.prepare(request) { progress ->
                    // Called from I/O threads; hop to the UI thread (FIFO, so updates stay in order).
                    scope.launch { applyProgress(progress) }
                }
                log("Command: ${command.redactedCommandLine().joinToString(" ")}")
                val process = GameProcess.start(command, scope)
                state = LaunchUiState.Running(process)
                log("Game started, PID ${process.pid}")
                val collector = scope.launch(Dispatchers.Default) { process.lines.collect { incoming += it } }
                val code = process.exitCode.await()
                delay(200)
                collector.cancel()
                state = LaunchUiState.Exited(code)
                log("Game exited with code $code", if (code == 0) LogLevel.INFO else LogLevel.ERROR)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.toString()
                state = LaunchUiState.Failed(message)
                log("Launch failed: $message", LogLevel.ERROR)
            }
        }
    }

    fun kill() {
        (state as? LaunchUiState.Running)?.process?.kill()
    }

    fun clearConsole() {
        incoming.clear()
        console.clear()
    }

    private fun applyProgress(progress: LaunchProgress) {
        if (state !is LaunchUiState.Preparing) return
        state = when (progress) {
            is LaunchProgress.Stage -> LaunchUiState.Preparing(progress.stage, progress.detail, null)
            is LaunchProgress.Download -> {
                val detail = (state as? LaunchUiState.Preparing)?.detail.orEmpty()
                LaunchUiState.Preparing(progress.stage, detail, progress.progress)
            }
        }
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        incoming += LogLine(LogSource.LAUNCHER, level, message)
    }

    private fun flushConsole() {
        if (incoming.isEmpty()) return
        val batch = generateSequence { incoming.poll() }.toList()
        console.addAll(batch)
        val overflow = console.size - MAX_CONSOLE_LINES
        if (overflow > 0) console.removeRange(0, overflow)
    }

    private companion object {
        const val MAX_CONSOLE_LINES = 10_000
    }
}
