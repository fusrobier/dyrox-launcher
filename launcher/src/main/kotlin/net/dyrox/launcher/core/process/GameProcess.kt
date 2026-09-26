package net.dyrox.launcher.core.process

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import net.dyrox.launcher.core.launch.LaunchCommand
import java.io.InputStream

/**
 * A running game. Streams stdout/stderr as parsed [LogLine]s and exposes the exit code.
 * Phase 4 builds instance supervision (status, memory, graceful stop via IPC) on top of this.
 */
class GameProcess private constructor(
    private val process: Process,
    val command: LaunchCommand,
) {
    private val _lines = MutableSharedFlow<LogLine>(
        replay = REPLAY_LINES,
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _exitCode = CompletableDeferred<Int>()

    /** Log records; late subscribers get the last [REPLAY_LINES] lines replayed. */
    val lines: SharedFlow<LogLine> = _lines.asSharedFlow()
    val exitCode: Deferred<Int> = _exitCode
    val pid: Long get() = process.pid()
    val isAlive: Boolean get() = process.isAlive

    /** Asks the OS to end the game. On Windows this is immediate, like [kill]. */
    fun stop() {
        process.destroy()
    }

    /** Force-kills the game and anything it spawned. */
    fun kill() {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }

    private fun start(scope: CoroutineScope) {
        val stdout = scope.launch(Dispatchers.IO) { pump(process.inputStream, LogSource.STDOUT) }
        val stderr = scope.launch(Dispatchers.IO) { pump(process.errorStream, LogSource.STDERR) }
        scope.launch(Dispatchers.IO) {
            val code = runInterruptible { process.waitFor() }
            joinAll(stdout, stderr)
            _exitCode.complete(code)
        }
    }

    private suspend fun pump(stream: InputStream, source: LogSource) {
        val parser = Log4jXmlParser(source)
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = runInterruptible { reader.readLine() } ?: break
                parser.feed(command.redact(line)).forEach { _lines.emit(it) }
            }
        }
        parser.flush().forEach { _lines.emit(it) }
    }

    companion object {
        const val REPLAY_LINES = 5000

        fun start(command: LaunchCommand, scope: CoroutineScope, environment: Map<String, String> = emptyMap()): GameProcess {
            val builder = ProcessBuilder(command.commandLine).directory(command.workingDirectory.toFile())
            builder.environment().putAll(environment)
            return GameProcess(builder.start(), command).also { it.start(scope) }
        }
    }
}
