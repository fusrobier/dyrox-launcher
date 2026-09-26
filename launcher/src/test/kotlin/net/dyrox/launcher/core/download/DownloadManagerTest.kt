package net.dyrox.launcher.core.download

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import net.dyrox.shared.hash.Hashing
import net.dyrox.shared.http.HttpService
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DownloadManagerTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var server: HttpServer
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private val content = mapOf(
        "/a.bin" to ByteArray(150_000) { (it % 256).toByte() },
        "/b.bin" to "hello dyrox".toByteArray(),
    )
    private val manager = DownloadManager(HttpService("dyrox-test"), parallelism = 4, maxAttempts = 3, progressIntervalMillis = 10)

    @BeforeTest
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            hits.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            val body = content[path]
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun stopServer() = server.stop(0)

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"
    private fun task(path: String, target: Path, sha1: String? = Hashing.sha1(content.getValue(path))) =
        DownloadTask(url(path), target, sha1, content[path]?.size?.toLong())
    private fun hitsFor(path: String) = hits[path]?.get() ?: 0

    @Test
    fun `downloads and verifies files into nested directories`(): Unit = runBlocking {
        val a = dir.resolve("x/y/a.bin")
        val b = dir.resolve("b.bin")
        var last: DownloadProgress? = null
        manager.downloadAll(listOf(task("/a.bin", a), task("/b.bin", b))) { last = it }

        assertContentEquals(content["/a.bin"], Files.readAllBytes(a))
        assertContentEquals(content["/b.bin"], Files.readAllBytes(b))
        assertEquals(2, last?.completedFiles)
        assertEquals(2, last?.totalFiles)
        assertEquals(1f, last?.fraction)
        assertFalse(Files.list(a.parent).use { s -> s.anyMatch { it.toString().endsWith(".part") } }, "temp file left behind")
    }

    @Test
    fun `valid existing files are not downloaded again`(): Unit = runBlocking {
        val a = dir.resolve("a.bin")
        manager.downloadAll(listOf(task("/a.bin", a)))
        manager.downloadAll(listOf(task("/a.bin", a)))
        assertEquals(1, hitsFor("/a.bin"))
    }

    @Test
    fun `corrupted existing files are replaced`(): Unit = runBlocking {
        val a = dir.resolve("a.bin")
        Files.write(a, ByteArray(150_000))
        manager.downloadAll(listOf(task("/a.bin", a)))
        assertContentEquals(content["/a.bin"], Files.readAllBytes(a))
    }

    @Test
    fun `checksum mismatch is retried then reported, and nothing is written`(): Unit = runBlocking {
        val target = dir.resolve("b.bin")
        val error = assertFailsWith<DownloadFailedException> {
            manager.downloadAll(listOf(task("/b.bin", target, sha1 = "0".repeat(40))))
        }
        assertEquals(1, error.failures.size)
        assertEquals(3, hitsFor("/b.bin"))
        assertFalse(Files.exists(target))
    }

    @Test
    fun `404 is not retried`(): Unit = runBlocking {
        assertFailsWith<DownloadFailedException> {
            manager.downloadAll(listOf(DownloadTask(url("/missing.bin"), dir.resolve("missing.bin"))))
        }
        assertEquals(1, hitsFor("/missing.bin"))
    }

    @Test
    fun `one failure does not stop the other downloads`(): Unit = runBlocking {
        val good = dir.resolve("a.bin")
        assertFailsWith<DownloadFailedException> {
            manager.downloadAll(listOf(task("/a.bin", good), DownloadTask(url("/missing.bin"), dir.resolve("m.bin"))))
        }
        assertContentEquals(content["/a.bin"], Files.readAllBytes(good))
    }
}
