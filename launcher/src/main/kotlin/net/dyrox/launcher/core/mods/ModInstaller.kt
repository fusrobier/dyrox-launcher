package net.dyrox.launcher.core.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.dyrox.launcher.core.download.DownloadManager
import net.dyrox.launcher.core.download.DownloadProgress
import net.dyrox.launcher.core.download.DownloadTask
import net.dyrox.shared.http.HttpService
import java.io.IOException
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class ModrinthVersion(
    val id: String,
    @SerialName("version_number") val versionNumber: String,
    @SerialName("version_type") val versionType: String = "release",
    @SerialName("date_published") val datePublished: String = "",
    val files: List<ModrinthFile> = emptyList(),
)

@Serializable
data class ModrinthFile(
    val url: String,
    val filename: String,
    val primary: Boolean = false,
    val size: Long? = null,
    val hashes: Map<String, String> = emptyMap(),
)

class ModNotAvailableException(message: String) : RuntimeException(message)

/** Installs required mods (Fabric API; the Dyrox client jar from Phase 5) into an instance's `mods` folder. */
class ModInstaller(
    private val http: HttpService,
    private val downloads: DownloadManager,
) {
    suspend fun modrinthVersions(project: String, gameVersion: String, loader: String): List<ModrinthVersion> {
        val loaders = URLEncoder.encode("[\"$loader\"]", Charsets.UTF_8)
        val gameVersions = URLEncoder.encode("[\"$gameVersion\"]", Charsets.UTF_8)
        return http.getJson(
            "$MODRINTH_API/project/$project/version?loaders=$loaders&game_versions=$gameVersions",
            ListSerializer(ModrinthVersion.serializer()),
        )
    }

    /** Makes sure the newest Fabric API for [gameVersion] is in [modsDir] and removes older copies. */
    suspend fun ensureFabricApi(modsDir: Path, gameVersion: String, onProgress: (DownloadProgress) -> Unit = {}): Path =
        withContext(Dispatchers.IO) {
            Files.createDirectories(modsDir)
            val versions = try {
                modrinthVersions(FABRIC_API_PROJECT, gameVersion, "fabric")
            } catch (e: IOException) {
                // Offline: keep whatever Fabric API is already installed.
                return@withContext installedFabricApi(modsDir).firstOrNull() ?: throw e
            }
            val version = versions.filter { it.versionType == "release" }.maxByOrNull { it.datePublished }
                ?: versions.maxByOrNull { it.datePublished }
                ?: throw ModNotAvailableException("No Fabric API build for Minecraft $gameVersion on Modrinth")
            val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
                ?: throw ModNotAvailableException("Fabric API ${version.versionNumber} has no files")

            require(file.filename.none { it == '/' || it == '\\' } && file.filename.endsWith(".jar")) {
                "Refusing suspicious file name ${file.filename}"
            }
            val target = modsDir.resolve(file.filename)
            downloads.downloadAll(listOf(DownloadTask(file.url, target, file.hashes["sha1"], file.size)), onProgress)
            installedFabricApi(modsDir).filter { it != target }.forEach(Files::deleteIfExists)
            target
        }

    private fun installedFabricApi(modsDir: Path): List<Path> {
        if (!Files.isDirectory(modsDir)) return emptyList()
        return Files.list(modsDir).use { files ->
            files.filter {
                val name = it.fileName.toString()
                name.startsWith("fabric-api-") && name.endsWith(".jar")
            }.toList()
        }
    }

    companion object {
        const val MODRINTH_API = "https://api.modrinth.com/v2"
        const val FABRIC_API_PROJECT = "fabric-api"
    }
}
