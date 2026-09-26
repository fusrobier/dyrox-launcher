package net.dyrox.shared.ipc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Game side of the IPC channel, used by the Dyrox client mod. Reads on a daemon thread so it
 * never blocks the render thread; requests are matched to responses by id.
 */
class IpcClient private constructor(private val socket: Socket, pid: Long, token: String, clientVersion: String?) : AutoCloseable {
    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<IpcMessage>>()
    private val nextRequestId = AtomicInteger(1)

    @Volatile
    var welcome: IpcMessage.Welcome? = null
        private set

    /** Called on the reader thread when the launcher asks the game to quit. */
    @Volatile
    var onShutdown: (() -> Unit)? = null

    /** Called on the reader thread when the connection is lost. */
    @Volatile
    var onDisconnected: (() -> Unit)? = null

    val isConnected: Boolean get() = !socket.isClosed

    init {
        send(IpcMessage.Hello(token, pid, clientVersion))
        Thread(::readLoop, "Dyrox IPC").apply { isDaemon = true }.start()
    }

    fun sendStatus(state: String, detail: String? = null) = send(IpcMessage.Status(state, detail))

    suspend fun requestSession(accountId: String, timeoutMillis: Long = 60_000): IpcSession {
        val response = request(timeoutMillis) { IpcMessage.SessionRequest(it, accountId) } as IpcMessage.SessionResponse
        return response.session ?: throw IOException(response.error ?: "The launcher returned no session")
    }

    suspend fun requestAccounts(timeoutMillis: Long = 10_000): List<IpcAccount> =
        (request(timeoutMillis) { IpcMessage.AccountsRequest(it) } as IpcMessage.AccountsResponse).accounts

    private suspend fun request(timeoutMillis: Long, build: (Int) -> IpcMessage): IpcMessage {
        val id = nextRequestId.getAndIncrement()
        val deferred = CompletableDeferred<IpcMessage>()
        pending[id] = deferred
        try {
            if (!send(build(id))) throw IOException("Not connected to the launcher")
            return withTimeout(timeoutMillis) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    @Synchronized
    private fun send(message: IpcMessage): Boolean = try {
        writer.write(IpcCodec.encode(message))
        writer.write("\n")
        writer.flush()
        true
    } catch (_: IOException) {
        false
    }

    private fun readLoop() {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            while (true) {
                val line = IpcCodec.readLine(reader) ?: break
                when (val message = IpcCodec.decode(line)) {
                    is IpcMessage.Welcome -> welcome = message
                    is IpcMessage.Shutdown -> onShutdown?.invoke()
                    is IpcMessage.SessionResponse -> pending[message.requestId]?.complete(message)
                    is IpcMessage.AccountsResponse -> pending[message.requestId]?.complete(message)
                    else -> Unit
                }
            }
        } catch (_: IOException) {
            // Connection closed.
        } finally {
            close()
            pending.values.forEach { it.completeExceptionally(IOException("Disconnected from the launcher")) }
            onDisconnected?.invoke()
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        fun connect(port: Int, token: String, pid: Long = ProcessHandle.current().pid(), clientVersion: String? = null): IpcClient {
            val socket = Socket()
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5_000)
            socket.tcpNoDelay = true
            return IpcClient(socket, pid, token, clientVersion)
        }

        /** Connects using the variables the launcher sets; null when the game wasn't started by Dyrox Launcher. */
        fun fromEnvironment(clientVersion: String? = null): IpcClient? {
            val port = System.getenv(IpcEnvironment.PORT)?.toIntOrNull() ?: return null
            val token = System.getenv(IpcEnvironment.TOKEN) ?: return null
            return runCatching { connect(port, token, clientVersion = clientVersion) }.getOrNull()
        }
    }
}
