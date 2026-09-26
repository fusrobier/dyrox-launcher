package net.dyrox.shared.ipc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Launcher side of the IPC channel: one loopback server for all instances. Each launch registers an
 * instance and gets a random token; the game proves which instance it is by sending that token.
 * Tokens are single-instance: a new registration replaces the previous one.
 */
class IpcServer(
    private val scope: CoroutineScope,
    private val handler: Handler,
) : AutoCloseable {
    interface Handler {
        fun onConnected(instanceId: String, hello: IpcMessage.Hello) {}

        /** Returns the reply to send, if any. */
        suspend fun onMessage(instanceId: String, message: IpcMessage): IpcMessage? = null

        fun onDisconnected(instanceId: String) {}
    }

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val tokenToInstance = ConcurrentHashMap<String, String>()
    private val connections = ConcurrentHashMap<String, Connection>()

    val port: Int = server.localPort

    init {
        scope.launch(Dispatchers.IO) { acceptLoop() }
    }

    /** Creates (or replaces) the token for [instanceId]. */
    fun register(instanceId: String): String {
        unregister(instanceId)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(RANDOM::nextBytes))
        tokenToInstance[token] = instanceId
        return token
    }

    fun unregister(instanceId: String) {
        tokenToInstance.entries.removeIf { it.value == instanceId }
        connections.remove(instanceId)?.close()
    }

    fun isConnected(instanceId: String): Boolean = connections.containsKey(instanceId)

    fun send(instanceId: String, message: IpcMessage): Boolean = connections[instanceId]?.send(message) ?: false

    override fun close() {
        runCatching { server.close() }
        connections.values.forEach { it.close() }
        connections.clear()
    }

    private fun acceptLoop() {
        while (!server.isClosed) {
            val socket = try {
                server.accept()
            } catch (_: IOException) {
                break
            }
            scope.launch(Dispatchers.IO) { serve(socket) }
        }
    }

    private suspend fun serve(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            // The peer must identify itself promptly.
            socket.soTimeout = HELLO_TIMEOUT_MILLIS
            val first = try {
                IpcCodec.readLine(reader)
            } catch (_: SocketTimeoutException) {
                return
            } catch (_: IOException) {
                return
            } ?: return
            val hello = IpcCodec.decode(first) as? IpcMessage.Hello ?: return
            val instanceId = tokenToInstance[hello.token] ?: return
            socket.soTimeout = 0

            val connection = Connection(socket)
            connections.put(instanceId, connection)?.close()
            handler.onConnected(instanceId, hello)
            try {
                while (true) {
                    val line = try {
                        IpcCodec.readLine(reader)
                    } catch (_: IOException) {
                        null
                    } ?: break
                    val message = IpcCodec.decode(line) ?: continue
                    handler.onMessage(instanceId, message)?.let(connection::send)
                }
            } finally {
                if (connections.remove(instanceId, connection)) handler.onDisconnected(instanceId)
            }
        }
    }

    private class Connection(private val socket: Socket) {
        private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

        @Synchronized
        fun send(message: IpcMessage): Boolean = try {
            writer.write(IpcCodec.encode(message))
            writer.write("\n")
            writer.flush()
            true
        } catch (_: IOException) {
            false
        }

        fun close() {
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val HELLO_TIMEOUT_MILLIS = 5_000
        val RANDOM = SecureRandom()
    }
}
