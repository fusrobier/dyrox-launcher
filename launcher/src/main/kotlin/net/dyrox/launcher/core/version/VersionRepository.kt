package net.dyrox.launcher.core.version

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dyrox.launcher.core.LauncherPaths
import net.dyrox.launcher.core.manifest.VersionManifest
import net.dyrox.launcher.core.manifest.VersionManifestService
import net.dyrox.shared.hash.Hashing
import net.dyrox.shared.http.ChecksumMismatchException
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.json.DyroxJson
import java.io.IOException
import java.nio.file.Files

class VersionNotFoundException(id: String) : RuntimeException("Version '$id' is not installed and not in Mojang's manifest")

/**
 * Loads version JSONs from `versions/<id>/<id>.json`, downloading (and SHA-1 verifying) vanilla
 * versions from the manifest when missing or outdated. Loader profiles (Fabric) must be installed first.
 */
class VersionRepository(
    private val paths: LauncherPaths,
    private val http: HttpService,
    private val manifests: VersionManifestService,
) {
    suspend fun load(id: String): VersionJson = withContext(Dispatchers.IO) {
        val file = paths.versionJson(id)
        val entry = manifestEntry(id)
        if (Files.isRegularFile(file) && (entry?.sha1 == null || Hashing.sha1(file).equals(entry.sha1, ignoreCase = true))) {
            return@withContext decode(Files.readAllBytes(file))
        }
        if (entry == null) throw VersionNotFoundException(id)

        val bytes = http.getBytes(entry.url)
        if (entry.sha1 != null && !Hashing.sha1(bytes).equals(entry.sha1, ignoreCase = true)) {
            throw ChecksumMismatchException("Version JSON for $id failed SHA-1 verification")
        }
        AtomicFiles.write(file, bytes)
        decode(bytes)
    }

    /** Ids of every version JSON present on disk (vanilla and loader profiles). */
    fun installedIds(): List<String> {
        if (!Files.isDirectory(paths.versionsDir)) return emptyList()
        return Files.list(paths.versionsDir).use { dirs ->
            dirs.filter { Files.isRegularFile(it.resolve("${it.fileName}.json")) }
                .map { it.fileName.toString() }
                .toList()
        }
    }

    /** Null when offline or when [id] is not a Mojang version (e.g. a Fabric profile). */
    private suspend fun manifestEntry(id: String): VersionManifest.Entry? =
        try {
            manifests.manifest().find(id)
        } catch (_: IOException) {
            null
        }

    private fun decode(bytes: ByteArray): VersionJson =
        DyroxJson.decodeFromString(VersionJson.serializer(), bytes.toString(Charsets.UTF_8))
}
