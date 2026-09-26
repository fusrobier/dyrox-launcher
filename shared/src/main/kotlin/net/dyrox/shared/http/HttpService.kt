package net.dyrox.shared.http

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import net.dyrox.shared.hash.Hashing.toHex
import net.dyrox.shared.json.DyroxJson
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

class HttpStatusException(val url: String, val statusCode: Int) : IOException("HTTP $statusCode for $url")

class ChecksumMismatchException(message: String) : IOException(message)

/** Result of streaming a response body to disk. */
data class DownloadedFile(val sha1: String, val bytes: Long)

/** A response whose status the caller interprets itself (auth APIs put error details in 4xx bodies). */
data class HttpResult(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299
}

/**
 * Thin coroutine wrapper around the JDK [HttpClient]. No third-party HTTP stack, so this class can be
 * bundled into the Minecraft mod without dependency clashes.
 */
class HttpService(
    val userAgent: String,
    private val client: HttpClient = defaultClient(),
    private val maxAttempts: Int = 3,
) {
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray = retryIo(maxAttempts) {
        val response = client.sendAsync(getRequest(url, headers), HttpResponse.BodyHandlers.ofByteArray()).await()
        response.requireSuccess(url)
        response.body()
    }

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        getBytes(url, headers).toString(Charsets.UTF_8)

    suspend fun <T> getJson(url: String, deserializer: DeserializationStrategy<T>, headers: Map<String, String> = emptyMap()): T =
        DyroxJson.decodeFromString(deserializer, getText(url, headers))

    /** GET without status checks or retries. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResult =
        execute(getRequest(url, headers))

    /** POST `application/x-www-form-urlencoded`, without status checks or retries. */
    suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): HttpResult {
        val body = fields.entries.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, Charsets.UTF_8) + "=" + URLEncoder.encode(value, Charsets.UTF_8)
        }
        return execute(post(url, "application/x-www-form-urlencoded", body, headers))
    }

    /** POST a JSON body, without status checks or retries. */
    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): HttpResult =
        execute(post(url, "application/json", json, headers + ("Accept" to "application/json")))

    private suspend fun execute(request: HttpRequest): HttpResult {
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8)).await()
        return HttpResult(response.statusCode(), response.body().orEmpty())
    }

    /**
     * Streams [url] into [target] while computing its SHA-1. Single attempt: callers that write to temp
     * files handle retries themselves (see `DownloadManager`).
     */
    suspend fun downloadTo(url: String, target: Path, onBytes: (Int) -> Unit = {}): DownloadedFile {
        val response = client.sendAsync(getRequest(url, emptyMap()), HttpResponse.BodyHandlers.ofInputStream()).await()
        if (response.statusCode() !in 200..299) {
            response.body().close()
            throw HttpStatusException(url, response.statusCode())
        }
        return withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-1")
            var total = 0L
            response.body().use { input ->
                Files.newOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        total += read
                        onBytes(read)
                    }
                }
            }
            DownloadedFile(digest.digest().toHex(), total)
        }
    }

    private fun getRequest(url: String, headers: Map<String, String>): HttpRequest =
        baseRequest(url, headers).GET().build()

    private fun post(url: String, contentType: String, body: String, headers: Map<String, String>): HttpRequest =
        baseRequest(url, headers + ("Content-Type" to contentType))
            .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
            .build()

    private fun baseRequest(url: String, headers: Map<String, String>): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", userAgent)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private fun HttpResponse<*>.requireSuccess(url: String) {
        if (statusCode() !in 200..299) throw HttpStatusException(url, statusCode())
    }

    companion object {
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

        fun defaultClient(): HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }
}

/**
 * Runs [block], retrying transient I/O failures with exponential backoff (500 ms, 1 s, 2 s, ...).
 * Client errors (4xx other than 429) are not retried.
 */
suspend fun <T> retryIo(maxAttempts: Int = 3, initialDelayMillis: Long = 500, block: suspend () -> T): T {
    var attempt = 1
    var delayMillis = initialDelayMillis
    while (true) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            val retryable = e !is HttpStatusException || e.statusCode == 429 || e.statusCode >= 500
            if (!retryable || attempt >= maxAttempts) throw e
            delay(delayMillis)
            delayMillis *= 2
            attempt++
        }
    }
}
