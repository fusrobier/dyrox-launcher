package net.dyrox.launcher.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.dyrox.shared.hash.Hashing
import net.dyrox.shared.http.ChecksumMismatchException
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.http.retryIo
import net.dyrox.shared.io.AtomicFiles
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class DownloadTask(
    val url: String,
    val target: Path,
    val sha1: String? = null,
    val size: Long? = null,
    /**
     * For content-addressed files (assets, whose path *is* their hash) an existing file of the right
     * size is trusted without re-hashing; hashing ~4000 assets on every launch would take seconds.
     */
    val trustExistingIfSizeMatches: Boolean = false,
)

data class DownloadProgress(
    val completedFiles: Int,
    val totalFiles: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = when {
            totalBytes > 0 -> (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
            totalFiles > 0 -> completedFiles.toFloat() / totalFiles
            else -> 1f
        }
}

class DownloadFailedException(val failures: List<Pair<DownloadTask, Throwable>>) : IOException(
    "${failures.size} download(s) failed; first: ${failures.first().first.url} (${failures.first().second.message})",
    failures.first().second,
)

/**
 * Parallel downloader. Every file is streamed to a temp file, verified (size and SHA-1) and only then
 * moved into place, so an interrupted or corrupted download never leaves a broken file behind.
 */
class DownloadManager(
    private val http: HttpService,
    private val parallelism: Int = 16,
    private val maxAttempts: Int = 3,
    private val progressIntervalMillis: Long = 100,
) {
    suspend fun downloadAll(tasks: Collection<DownloadTask>, onProgress: (DownloadProgress) -> Unit = {}) {
        val unique = tasks.distinctBy { it.target.toAbsolutePath().normalize() }
        val pending = findPending(unique)
        if (pending.isEmpty()) {
            onProgress(DownloadProgress(0, 0, 0, 0))
            return
        }

        val totalBytes = pending.sumOf { it.size ?: 0L }
        val downloadedBytes = AtomicLong()
        val completedFiles = AtomicInteger()
        val failures = ConcurrentLinkedQueue<Pair<DownloadTask, Throwable>>()
        val permits = Semaphore(parallelism)
        // Some files (e.g. Fabric libraries) declare no size, so the byte total is a lower bound.
        fun snapshot(): DownloadProgress {
            val downloaded = downloadedBytes.get()
            return DownloadProgress(completedFiles.get(), pending.size, downloaded, maxOf(totalBytes, downloaded))
        }

        coroutineScope {
            val reporter = launch {
                while (isActive) {
                    onProgress(snapshot())
                    delay(progressIntervalMillis)
                }
            }
            supervisorScope {
                for (task in pending) {
                    launch(Dispatchers.IO) {
                        permits.withPermit {
                            try {
                                downloadOne(task, downloadedBytes)
                                completedFiles.incrementAndGet()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                failures += task to e
                            }
                        }
                    }
                }
            }
            reporter.cancel()
        }
        onProgress(snapshot())
        if (failures.isNotEmpty()) throw DownloadFailedException(failures.toList())
    }

    private suspend fun findPending(tasks: List<DownloadTask>): List<DownloadTask> = coroutineScope {
        val checkPermits = Semaphore(parallelism)
        tasks.map { task ->
            async(Dispatchers.IO) { checkPermits.withPermit { task.takeUnless { isUpToDate(it) } } }
        }.awaitAll().filterNotNull()
    }

    private fun isUpToDate(task: DownloadTask): Boolean {
        if (!Files.isRegularFile(task.target)) return false
        val size = Files.size(task.target)
        if (task.size != null && size != task.size) return false
        if (task.sha1 == null) return true
        if (task.trustExistingIfSizeMatches && task.size != null) return true
        return Hashing.sha1(task.target).equals(task.sha1, ignoreCase = true)
    }

    private suspend fun downloadOne(task: DownloadTask, downloadedBytes: AtomicLong) {
        retryIo(maxAttempts) {
            withContext(Dispatchers.IO) {
                task.target.parent?.let(Files::createDirectories)
                val temp = AtomicFiles.tempSibling(task.target)
                var received = 0L
                try {
                    val result = http.downloadTo(task.url, temp) { bytes ->
                        received += bytes
                        downloadedBytes.addAndGet(bytes.toLong())
                    }
                    if (task.size != null && result.bytes != task.size) {
                        throw ChecksumMismatchException("${task.url}: expected ${task.size} bytes, got ${result.bytes}")
                    }
                    if (task.sha1 != null && !result.sha1.equals(task.sha1, ignoreCase = true)) {
                        throw ChecksumMismatchException("${task.url}: SHA-1 ${result.sha1} does not match ${task.sha1}")
                    }
                    AtomicFiles.move(temp, task.target)
                } catch (e: Throwable) {
                    // Undo this attempt's progress so a retry doesn't count bytes twice.
                    downloadedBytes.addAndGet(-received)
                    throw e
                } finally {
                    Files.deleteIfExists(temp)
                }
            }
        }
    }
}
