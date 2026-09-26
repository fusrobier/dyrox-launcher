package net.dyrox.launcher.core.fabric

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.dyrox.launcher.core.LauncherPaths
import net.dyrox.launcher.core.version.VersionJson
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.json.DyroxJson
import java.io.IOException
import java.net.URLEncoder
import java.nio.file.Files

@Serializable
data class FabricGameVersion(val version: String, val stable: Boolean = false)

@Serializable
data class FabricLoaderVersion(val version: String, val stable: Boolean = false)

@Serializable
private data class FabricLoaderEntry(val loader: FabricLoaderVersion)

class FabricUnsupportedException(gameVersion: String) : RuntimeException("Fabric does not support Minecraft $gameVersion")

/**
 * Installs a Fabric loader profile (`fabric-loader-<loader>-<game>.json`) from Fabric's meta API.
 * The profile `inheritsFrom` the vanilla version, so the normal resolver takes it from there.
 */
class FabricInstaller(
    private val paths: LauncherPaths,
    private val http: HttpService,
) {
    suspend fun supportedGameVersions(): List<FabricGameVersion> =
        http.getJson("$META/v2/versions/game", ListSerializer(FabricGameVersion.serializer()))

    suspend fun loaderVersions(gameVersion: String): List<FabricLoaderVersion> =
        http.getJson("$META/v2/versions/loader/${encode(gameVersion)}", ListSerializer(FabricLoaderEntry.serializer()))
            .map { it.loader }

    /** Installs the profile and returns its version id. [loaderVersion] null means the newest stable loader. */
    suspend fun install(gameVersion: String, loaderVersion: String? = null): String = withContext(Dispatchers.IO) {
        val loader = loaderVersion ?: try {
            loaderVersions(gameVersion).firstOrNull { it.stable }?.version ?: throw FabricUnsupportedException(gameVersion)
        } catch (e: IOException) {
            // Offline: reuse the most recently installed profile for this game version.
            return@withContext installedProfiles(gameVersion).firstOrNull() ?: throw e
        }

        val id = profileId(loader, gameVersion)
        val file = paths.versionJson(id)
        if (!Files.isRegularFile(file)) {
            val bytes = http.getBytes("$META/v2/versions/loader/${encode(gameVersion)}/${encode(loader)}/profile/json")
            val profile = DyroxJson.decodeFromString(VersionJson.serializer(), bytes.toString(Charsets.UTF_8))
            check(profile.id == id && profile.inheritsFrom == gameVersion) {
                "Unexpected Fabric profile ${profile.id} (inherits ${profile.inheritsFrom}), expected $id"
            }
            AtomicFiles.write(file, bytes)
        }
        id
    }

    /** Installed Fabric profile ids for [gameVersion], newest first. */
    fun installedProfiles(gameVersion: String): List<String> {
        if (!Files.isDirectory(paths.versionsDir)) return emptyList()
        return Files.list(paths.versionsDir).use { dirs ->
            dirs.filter {
                val name = it.fileName.toString()
                name.startsWith("fabric-loader-") && name.endsWith("-$gameVersion") && Files.isRegularFile(it.resolve("$name.json"))
            }
                .sorted(compareByDescending { Files.getLastModifiedTime(it) })
                .map { it.fileName.toString() }
                .toList()
        }
    }

    companion object {
        const val META = "https://meta.fabricmc.net"

        fun profileId(loaderVersion: String, gameVersion: String) = "fabric-loader-$loaderVersion-$gameVersion"

        private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
    }
}
