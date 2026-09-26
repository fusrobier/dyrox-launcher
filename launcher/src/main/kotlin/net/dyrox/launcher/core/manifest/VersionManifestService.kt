package net.dyrox.launcher.core.manifest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.json.DyroxJson
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fetches the version manifest once per session and caches it on disk, so the launcher still
 * lists (and launches installed) versions while offline.
 */
class VersionManifestService(
    private val http: HttpService,
    private val cacheFile: Path,
) {
    private val mutex = Mutex()
    private var cached: VersionManifest? = null

    suspend fun manifest(forceRefresh: Boolean = false): VersionManifest = mutex.withLock {
        val current = cached
        if (current != null && !forceRefresh) return@withLock current
        val manifest = withContext(Dispatchers.IO) {
            try {
                val bytes = http.getBytes(URL)
                val parsed = decode(bytes.toString(Charsets.UTF_8))
                AtomicFiles.write(cacheFile, bytes)
                parsed
            } catch (e: IOException) {
                if (!Files.isRegularFile(cacheFile)) throw e
                decode(Files.readString(cacheFile))
            }
        }
        cached = manifest
        manifest
    }

    private fun decode(text: String) = DyroxJson.decodeFromString(VersionManifest.serializer(), text)

    companion object {
        const val URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }
}
