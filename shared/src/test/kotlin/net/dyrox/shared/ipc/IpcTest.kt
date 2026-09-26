package net.dyrox.shared.ipc

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.dyrox.shared.account.AccountType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IpcTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connected = CopyOnWriteArrayList<String>()
    private val disconnected = CompletableDeferred<String>()
    private val server = IpcServer(scope, object : IpcServer.Handler {
        override fun onConnected(instanceId: String, hello: IpcMessage.Hello) {
            connected += instanceId
        }

        override suspend fun onMessage(instanceId: String, message: IpcMessage): IpcMessage? = when (message) {
            is IpcMessage.AccountsRequest -> IpcMessage.AccountsResponse(
                message.requestId,
                listOf(IpcAccount("a1", "Steve", "0".repeat(32), AccountType.OFFLINE)),
            )
            is IpcMessage.SessionRequest -> IpcMessage.SessionResponse(message.requestId, error = "no session for ${message.accountId}")
            else -> null
        }

        override fun onDisconnected(instanceId: String) {
            disconnected.complete(instanceId)
        }
    })

    @AfterTest
    fun tearDown() {
        server.close()
        scope.cancel()
    }

    @Test
    fun `client with a valid token can make requests and receive shutdown`(): Unit = runBlocking {
        val token = server.register("instance-1")
        val client = IpcClient.connect(server.port, token, pid = 42, clientVersion = "test")
        val shutdown = CompletableDeferred<Unit>()
        client.onShutdown = { shutdown.complete(Unit) }

        val accounts = client.requestAccounts()
        assertEquals(listOf("Steve"), accounts.map { it.username })
        assertEquals(listOf("instance-1"), connected)
        assertTrue(server.isConnected("instance-1"))

        val error = runCatching { client.requestSession("a1") }.exceptionOrNull()
        assertEquals("no session for a1", error?.message)

        assertTrue(server.send("instance-1", IpcMessage.Shutdown))
        withTimeout(5_000) { shutdown.await() }

        client.close()
        assertEquals("instance-1", withTimeout(5_000) { disconnected.await() })
    }

    @Test
    fun `wrong token is rejected`(): Unit = runBlocking {
        server.register("instance-1")
        val lost = CompletableDeferred<Unit>()
        val client = IpcClient.connect(server.port, "not-the-token")
        client.onDisconnected = { lost.complete(Unit) }
        withTimeout(5_000) { lost.await() }
        assertTrue(connected.isEmpty())
        assertTrue(!server.isConnected("instance-1"))
    }

    @Test
    fun `re-registering an instance invalidates the old token`(): Unit = runBlocking {
        val old = server.register("instance-1")
        server.register("instance-1")
        val lost = CompletableDeferred<Unit>()
        IpcClient.connect(server.port, old).onDisconnected = { lost.complete(Unit) }
        withTimeout(5_000) { lost.await() }
        assertTrue(connected.isEmpty())
    }

    @Test
    fun `malformed and unknown lines are ignored`(): Unit = runBlocking {
        val token = server.register("raw")
        Socket(InetAddress.getLoopbackAddress(), server.port).use { socket ->
            val out = socket.getOutputStream().bufferedWriter()
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            out.write(IpcCodec.encode(IpcMessage.Hello(token, 1)) + "\n")
            out.write("this is not json\n")
            out.write("""{"type":"from_the_future","x":1}""" + "\n")
            out.write(IpcCodec.encode(IpcMessage.AccountsRequest(7)) + "\n")
            out.flush()
            val reply = IpcCodec.decode(input.readLine())
            assertIs<IpcMessage.AccountsResponse>(reply)
            assertEquals(7, reply.requestId)
        }
    }

    @Test
    fun `codec round-trips every message and never prints the token`() {
        val hello = IpcMessage.Hello("secret-token", 1)
        assertEquals(hello, IpcCodec.decode(IpcCodec.encode(hello)))
        assertTrue("secret-token" !in hello.toString())
        assertEquals(IpcMessage.Shutdown, IpcCodec.decode(IpcCodec.encode(IpcMessage.Shutdown)))
        assertTrue(""""type":"shutdown"""" in IpcCodec.encode(IpcMessage.Shutdown))
        assertNull(IpcCodec.decode("""{"type":"hello"}"""))
    }
}
