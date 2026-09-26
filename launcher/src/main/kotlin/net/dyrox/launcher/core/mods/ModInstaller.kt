package net.dyrox.launcher.core.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.dyrox.launcher.core.download.DownloadManager
import net.dyrox.launcher.core.download.DownloadProgress
import net.dyrox.launcher.core.download.DownloadTask
import net.dyrox.shared.hash.Hashing
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.io.AtomicFiles
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

/** Installs required mods (Fabric API, Fabric Language Kotlin, the bundled Dyrox client) into an instance's `mods` folder. */
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
        ensureModrinthMod(FABRIC_API_PROJECT, "fabric-api-", "Fabric API", modsDir, gameVersion, onProgress)

    /** Fabric Language Kotlin: the Kotlin runtime the Dyrox client needs. */
    suspend fun ensureFabricLanguageKotlin(modsDir: Path, gameVersion: String, onProgress: (DownloadProgress) -> Unit = {}): Path =
        ensureModrinthMod(FABRIC_LANGUAGE_KOTLIN_PROJECT, "fabric-language-kotlin-", "Fabric Language Kotlin", modsDir, gameVersion, onProgress)

    /**
     * Installs the newest release of a Modrinth [project] for [gameVersion] (SHA-1 verified) and removes
     * older copies, recognised by [filePrefix]. Offline, an already installed copy is kept.
     */
    private suspend fun ensureModrinthMod(
        project: String,
        filePrefix: String,
        displayName: String,
        modsDir: Path,
        gameVersion: String,
        onProgress: (DownloadProgress) -> Unit,
    ): Path = withContext(Dispatchers.IO) {
        Files.createDirectories(modsDir)
        val versions = try {
            modrinthVersions(project, gameVersion, "fabric")
        } catch (e: IOException) {
            return@withContext installed(modsDir, filePrefix).firstOrNull() ?: throw e
        }
        val version = versions.filter { it.versionType == "release" }.maxByOrNull { it.datePublished }
            ?: versions.maxByOrNull { it.datePublished }
            ?: throw ModNotAvailableException("No $displayName build for Minecraft $gameVersion on Modrinth")
        val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
            ?: throw ModNotAvailableException("$displayName ${version.versionNumber} has no files")

        require(file.filename.none { it == '/' || it == '\\' } && file.filename.endsWith(".jar") && file.filename.startsWith(filePrefix)) {
            "Refusing unexpected file name ${file.filename}"
        }
        val target = modsDir.resolve(file.filename)
        downloads.downloadAll(listOf(DownloadTask(file.url, target, file.hashes["sha1"], file.size)), onProgress)
        installed(modsDir, filePrefix).filter { it != target }.forEach(Files::deleteIfExists)
        target
    }

    /** What the launcher carries of the Dyrox client (null in builds without it, e.g. some dev setups). */
    val bundledClient: BundledClient? by lazy { BundledClient.load() }

    /**
     * Copies the bundled Dyrox client into [modsDir] when it's built for [gameVersion] (updating it if the
     * bundled jar changed). Returns null if there's no client for this version.
     */
    suspend fun ensureDyroxClient(modsDir: Path, gameVersion: String): Path? = withContext(Dispatchers.IO) {
        val client = bundledClient?.takeIf { it.minecraftVersion == gameVersion } ?: return@withContext null
        Files.createDirectories(modsDir)
        val target = modsDir.resolve(BundledClient.FILE_NAME)
        if (!Files.isRegularFile(target) || Hashing.sha1(target) != client.sha1) AtomicFiles.write(target, client.bytes())
        target
    }

    /** Removes the Dyrox client from an instance that has it switched off (only our own file). */
    fun removeDyroxClient(modsDir: Path) {
        Files.deleteIfExists(modsDir.resolve(BundledClient.FILE_NAME))
    }

    private fun installed(modsDir: Path, prefix: String): List<Path> {
        if (!Files.isDirectory(modsDir)) return emptyList()
        return Files.list(modsDir).use { files ->
            files.filter {
                val name = it.fileName.toString()
                name.startsWith(prefix) && name.endsWith(".jar")
            }.toList()
        }
    }

    companion object {
        const val MODRINTH_API = "https://api.modrinth.com/v2"
        const val FABRIC_API_PROJECT = "fabric-api"
        const val FABRIC_LANGUAGE_KOTLIN_PROJECT = "fabric-language-kotlin"
    }
}

/** The Dyrox client jar packaged inside the launcher (`/bundled-mods/`), built from `:client`. */
class BundledClient(val version: String, val minecraftVersion: String, private val resource: String) {
    val sha1: String by lazy { Hashing.sha1(bytes()) }

    fun bytes(): ByteArray = requireNotNull(BundledClient::class.java.getResourceAsStream(resource)) { "Missing $resource" }.use { it.readBytes() }

    companion object {
        const val FILE_NAME = "dyrox-client.jar"
        private const val DIR = "/bundled-mods/"

        fun load(): BundledClient? {
            val properties = BundledClient::class.java.getResourceAsStream("${DIR}dyrox-client.properties")?.use { stream ->
                java.util.Properties().apply { load(stream) }
            } ?: return null
            if (BundledClient::class.java.getResource(DIR + FILE_NAME) == null) return null
            return BundledClient(
                version = properties.getProperty("version", "unknown"),
                minecraftVersion = properties.getProperty("minecraft_version") ?: return null,
                resource = DIR + FILE_NAME,
            )
        }
    }
}
