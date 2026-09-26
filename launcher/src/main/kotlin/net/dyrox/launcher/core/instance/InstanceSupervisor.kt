package net.dyrox.launcher.core.instance

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.dyrox.launcher.core.LaunchProgress
import net.dyrox.launcher.core.LaunchStage
import net.dyrox.launcher.core.download.DownloadProgress
import net.dyrox.launcher.core.launch.LaunchCommand
import net.dyrox.launcher.core.launch.LaunchIdentity
import net.dyrox.launcher.core.process.GameProcess
import net.dyrox.launcher.core.process.LogLevel
import net.dyrox.launcher.core.process.LogLine
import net.dyrox.launcher.core.process.LogSource
import net.dyrox.shared.account.AccountManager
import net.dyrox.shared.account.StoredAccount
import net.dyrox.shared.ipc.IpcAccount
import net.dyrox.shared.ipc.IpcEnvironment
import net.dyrox.shared.ipc.IpcMessage
import net.dyrox.shared.ipc.IpcServer
import net.dyrox.shared.ipc.IpcSession
import java.nio.file.Path

sealed interface InstanceState {
    /** Preparing, starting, running or stopping: the instance holds its game folder and account. */
    val isActive: Boolean get() = false

    data class Preparing(val stage: LaunchStage, val detail: String, val download: DownloadProgress?) : InstanceState {
        override val isActive get() = true
    }

    /** Process started, game still loading. */
    data object Starting : InstanceState {
        override val isActive get() = true
    }

    data object Running : InstanceState {
        override val isActive get() = true
    }

    data object Stopping : InstanceState {
        override val isActive get() = true
    }

    data class Exited(val code: Int) : InstanceState

    data class Crashed(val code: Int, val crashReport: Path?) : InstanceState

    data class Failed(val message: String) : InstanceState
}

/** Turns an instance + account into a ready launch command (download, install, build arguments). */
fun interface LaunchPreparer {
    suspend fun prepare(
        instance: InstanceConfig,
        identity: LaunchIdentity,
        systemProperties: Map<String, String>,
        onProgress: (LaunchProgress) -> Unit,
    ): LaunchCommand
}

class AccountInUseException(message: String) : IllegalStateException(message)

/** One launch of one instance. Kept after the game exits so its log and crash report stay viewable. */
class InstanceSession(
    val instance: InstanceConfig,
    account: StoredAccount,
    val gameDirectory: Path,
) {
    val log = LogBuffer()

    internal val mutableState = MutableStateFlow<InstanceState>(InstanceState.Preparing(LaunchStage.RESOLVING, "", null))
    val state: StateFlow<InstanceState> = mutableState.asStateFlow()

    internal val mutableMemory = MutableStateFlow<Long?>(null)
    val memoryBytes: StateFlow<Long?> = mutableMemory.asStateFlow()

    /** Can change while running when the in-game alt manager switches accounts. */
    @Volatile
    var account: StoredAccount = account
        internal set

    @Volatile
    var pid: Long? = null
        internal set

    @Volatile
    var startedAt: Long? = null
        internal set

    @Volatile
    var ipcConnected: Boolean = false
        internal set

    @Volatile internal var process: GameProcess? = null
    @Volatile internal var job: Job? = null
    @Volatile internal var stopRequested = false
    @Volatile internal var killRequested = false
    @Volatile internal var crashDetected = false

    val isActive: Boolean get() = state.value.isActive

    internal fun launcherLog(message: String, level: LogLevel = LogLevel.INFO) = log.append(LogLine(LogSource.LAUNCHER, level, message))
}

/**
 * Runs any number of instances side by side, each in its own process with its own account,
 * game folder (locked for the duration), log, and IPC token.
 */
class InstanceSupervisor(
    private val repository: InstanceRepository,
    private val accounts: AccountManager,
    private val preparer: LaunchPreparer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Pause between starts in a multi-account launch, so JVMs don't all load at once. */
    private val staggerMillis: Long = 2_000,
) : IpcServer.Handler, AutoCloseable {
    private val _sessions = MutableStateFlow<Map<String, InstanceSession>>(emptyMap())
    /** Latest session per instance id, including finished ones. */
    val sessions: StateFlow<Map<String, InstanceSession>> = _sessions.asStateFlow()

    /** Serialises installs so parallel launches never download into the shared store at the same time. */
    private val installMutex = Mutex()
    private val ipcServer = lazy { IpcServer(scope, this) }
    private val ipc: IpcServer by ipcServer

    val activeSessions: List<InstanceSession> get() = _sessions.value.values.filter { it.isActive }

    /** Starts [instanceId] with [accountId], else the instance's own account, else the selected one. */
    @Synchronized
    fun launch(instanceId: String, accountId: String? = null): InstanceSession {
        val instance = requireNotNull(repository.find(instanceId)) { "Unknown instance $instanceId" }
        _sessions.value[instanceId]?.takeIf { it.isActive }?.let { throw IllegalStateException("${instance.name} is already running") }
        val contents = accounts.contents.value
        val account = (accountId ?: instance.accountId)?.let { id -> contents.accounts.firstOrNull { it.id == id } }
            ?: contents.selected
            ?: throw IllegalStateException("No account selected. Add one on the Accounts screen.")
        activeSessions.firstOrNull { it.account.id == account.id }?.let {
            throw AccountInUseException("${account.username} is already playing in \"${it.instance.name}\"")
        }
        val session = InstanceSession(instance, account, repository.gameDirectory(instance))
        _sessions.value = _sessions.value + (instanceId to session)
        session.job = scope.launch { run(session) }
        return session
    }

    /**
     * "Launch with selected accounts": one instance per account. The first uses [templateId], the others
     * use its siblings (`<name> #2`, `#3`, ...) which mirror the template's settings but have their own
     * game folders. Instances that are already running are skipped over.
     */
    suspend fun launchWithAccounts(templateId: String, accountIds: List<String>): List<InstanceSession> {
        require(accountIds.isNotEmpty()) { "Select at least one account" }
        val template = requireNotNull(repository.find(templateId)) { "Unknown instance $templateId" }
        val busy = activeSessions.filter { session -> session.account.id in accountIds }
        if (busy.isNotEmpty()) {
            throw AccountInUseException(busy.joinToString { "${it.account.username} is already playing in \"${it.instance.name}\"" })
        }
        var nextIndex = 1
        fun nextTarget(): InstanceConfig {
            while (true) {
                val candidate = if (nextIndex == 1) template else repository.siblingFor(template, nextIndex)
                nextIndex++
                if (_sessions.value[candidate.id]?.isActive != true) return candidate
            }
        }
        return accountIds.mapIndexed { index, accountId ->
            if (index > 0) delay(staggerMillis)
            launch(nextTarget().id, accountId)
        }
    }

    /** Graceful stop: IPC shutdown (Dyrox client), else WM_CLOSE (Windows), else SIGTERM. */
    fun stop(instanceId: String) {
        val session = _sessions.value[instanceId] ?: return
        if (!session.isActive) return
        session.stopRequested = true
        val process = session.process
        if (process == null) {
            // Still preparing: nothing to close yet, just cancel.
            session.job?.cancel()
            return
        }
        session.mutableState.value = InstanceState.Stopping
        when {
            ipc.send(instanceId, IpcMessage.Shutdown) -> session.launcherLog("Asked the game to quit (Dyrox client)")
            WindowControl.requestClose(process.pid) -> session.launcherLog("Asked the game window to close")
            else -> {
                session.launcherLog("Sent a stop signal to the game")
                process.stop()
            }
        }
    }

    fun kill(instanceId: String) {
        val session = _sessions.value[instanceId] ?: return
        session.killRequested = true
        session.stopRequested = true
        session.process?.kill() ?: session.job?.cancel()
        session.launcherLog("Killed by user", LogLevel.WARN)
    }

    fun stopAll() = activeSessions.forEach { stop(it.instance.id) }

    fun killAll() = activeSessions.forEach { kill(it.instance.id) }

    /** Waits until nothing is running; false on timeout. */
    suspend fun awaitAllStopped(timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            while (activeSessions.isNotEmpty()) delay(200)
            true
        } ?: false

    override fun close() {
        if (ipcServer.isInitialized()) runCatching { ipc.close() }
    }

    private suspend fun run(session: InstanceSession) {
        val instance = session.instance
        var lock: GameDirLock? = null
        val jobs = ArrayList<Job>()
        var registered = false
        try {
            lock = withContext(Dispatchers.IO) { GameDirLock.acquire(session.gameDirectory) }
            val gameSession = accounts.session(session.account.id)
            val properties = mapOf(
                "dyrox.instance.id" to instance.id,
                "dyrox.instance.name" to instance.name,
                "dyrox.profile" to instance.configProfile,
            )
            if (installMutex.isLocked) session.mutableState.value = InstanceState.Preparing(LaunchStage.RESOLVING, "Waiting for another instance to finish installing", null)
            val command = installMutex.withLock {
                preparer.prepare(instance, LaunchIdentity.from(gameSession), properties) { progress ->
                    if (session.state.value is InstanceState.Preparing) session.mutableState.value = progress.toState(session.state.value)
                }
            }

            val token = ipc.register(instance.id)
            registered = true
            val environment = mapOf(
                IpcEnvironment.PORT to ipc.port.toString(),
                IpcEnvironment.TOKEN to token,
                IpcEnvironment.INSTANCE_ID to instance.id,
            )
            session.launcherLog("Launching ${instance.gameVersion} (${instance.loader.label}) as ${session.account.username} [${session.account.type.label}]")
            session.launcherLog("Command: ${command.redactedCommandLine().joinToString(" ")}")
            val process = GameProcess.start(command, scope, environment)
            session.process = process
            session.pid = process.pid
            session.startedAt = clock()
            lock.recordPid(process.pid)
            if (!session.ipcConnected) session.mutableState.value = InstanceState.Starting
            session.launcherLog("Game started, PID ${process.pid}")
            repository.markPlayed(instance.id)

            jobs += scope.launch { process.lines.collect { line -> onGameLine(session, line) } }
            jobs += scope.launch(Dispatchers.IO) {
                while (isActive) {
                    session.mutableMemory.value = ProcessMetrics.residentBytes(process.pid)
                    delay(2_000)
                }
            }
            jobs += scope.launch(Dispatchers.IO) {
                val suffix = " — ${instance.name} · ${session.account.username}"
                while (isActive) {
                    // The Dyrox client sets its own title; this covers vanilla and other versions (Windows only).
                    if (!session.ipcConnected) WindowControl.ensureTitleSuffix(process.pid, suffix)
                    delay(3_000)
                }
            }

            val code = process.exitCode.await()
            delay(250) // let the log collector drain the last lines
            val crashReport = session.startedAt?.let { withContext(Dispatchers.IO) { CrashReports.find(session.gameDirectory, it) } }
            session.mutableState.value = when {
                session.killRequested -> InstanceState.Exited(code)
                session.crashDetected -> InstanceState.Crashed(code, crashReport)
                code != 0 && !session.stopRequested -> InstanceState.Crashed(code, crashReport)
                else -> InstanceState.Exited(code)
            }
            session.launcherLog("Game exited with code $code", if (session.state.value is InstanceState.Crashed) LogLevel.ERROR else LogLevel.INFO)
            crashReport?.let { session.launcherLog("Crash report: $it", LogLevel.ERROR) }
        } catch (e: CancellationException) {
            session.mutableState.value = InstanceState.Failed("Launch cancelled")
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.toString()
            session.mutableState.value = InstanceState.Failed(message)
            session.launcherLog("Launch failed: $message", LogLevel.ERROR)
        } finally {
            jobs.forEach { it.cancel() }
            if (registered) ipc.unregister(instance.id)
            session.ipcConnected = false
            session.mutableMemory.value = null
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { lock?.close() }
        }
    }

    private fun onGameLine(session: InstanceSession, line: LogLine) {
        session.log.append(line)
        if (session.state.value == InstanceState.Starting && READY_MARKERS.any { it in line.message }) {
            session.mutableState.value = InstanceState.Running
        }
        if (CRASH_MARKERS.any { it in line.message }) session.crashDetected = true
    }

    private fun LaunchProgress.toState(current: InstanceState): InstanceState = when (this) {
        is LaunchProgress.Stage -> InstanceState.Preparing(stage, detail, null)
        is LaunchProgress.Download -> InstanceState.Preparing(stage, (current as? InstanceState.Preparing)?.detail.orEmpty(), progress)
    }

    // --- IPC (Dyrox client in the game) ---

    override fun onConnected(instanceId: String, hello: IpcMessage.Hello) {
        val session = _sessions.value[instanceId] ?: return
        session.ipcConnected = true
        if (session.state.value == InstanceState.Starting) session.mutableState.value = InstanceState.Running
        session.launcherLog("Dyrox client connected (${hello.clientVersion ?: "unknown version"})")
        ipc.send(instanceId, IpcMessage.Welcome(instanceId, session.instance.name))
    }

    override suspend fun onMessage(instanceId: String, message: IpcMessage): IpcMessage? {
        val session = _sessions.value[instanceId] ?: return null
        return when (message) {
            is IpcMessage.Status -> {
                session.launcherLog("Client status: ${message.state}" + message.detail?.let { " ($it)" }.orEmpty())
                null
            }
            is IpcMessage.AccountsRequest -> {
                val inUseElsewhere = activeSessions.filter { it !== session }.map { it.account.id }.toSet()
                IpcMessage.AccountsResponse(
                    message.requestId,
                    accounts.contents.value.accounts.map { IpcAccount(it.id, it.username, it.uuid, it.type, inUse = it.id in inUseElsewhere) },
                )
            }
            is IpcMessage.SessionRequest -> sessionFor(session, message)
            else -> null
        }
    }

    override fun onDisconnected(instanceId: String) {
        _sessions.value[instanceId]?.ipcConnected = false
    }

    /** In-game account switch: hands out a fresh session unless that account is playing elsewhere. */
    private suspend fun sessionFor(session: InstanceSession, request: IpcMessage.SessionRequest): IpcMessage.SessionResponse {
        activeSessions.firstOrNull { it !== session && it.account.id == request.accountId }?.let {
            return IpcMessage.SessionResponse(request.requestId, error = "That account is already playing in \"${it.instance.name}\"")
        }
        return try {
            val game = accounts.session(request.accountId)
            accounts.contents.value.accounts.firstOrNull { it.id == game.accountId }?.let { session.account = it }
            session.launcherLog("Switched account to ${game.username}")
            IpcMessage.SessionResponse(
                request.requestId,
                IpcSession(game.accountId, game.username, game.uuid, game.accessToken, game.xuid, game.type),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            IpcMessage.SessionResponse(request.requestId, error = e.message ?: "Could not get a session")
        }
    }

    companion object {
        /** Log lines that mean the game window is up and loading is done enough to play. */
        val READY_MARKERS = listOf("Sound engine started", "LWJGL Version:", "Backend library: LWJGL")
        val CRASH_MARKERS = listOf("Minecraft has crashed!", "#@!@# Game crashed!", "This crash report has been saved to")
    }
}
