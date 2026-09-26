package net.dyrox.launcher.core.install

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dyrox.launcher.core.LauncherPaths
import net.dyrox.launcher.core.download.DownloadManager
import net.dyrox.launcher.core.download.DownloadProgress
import net.dyrox.launcher.core.download.DownloadTask
import net.dyrox.launcher.core.library.LibraryResolver
import net.dyrox.launcher.core.rules.RuleContext
import net.dyrox.launcher.core.version.ResolvedVersion
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.json.DyroxJson
import net.dyrox.shared.platform.Platform
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MissingGameFileException(message: String) : RuntimeException(message)

/** Downloads and verifies the client jar, libraries, natives, log config and assets of a version. */
class GameInstaller(
    private val paths: LauncherPaths,
    private val downloads: DownloadManager,
) {
    suspend fun install(
        version: ResolvedVersion,
        platform: Platform,
        onProgress: (DownloadProgress) -> Unit = {},
    ): InstalledGame = withContext(Dispatchers.IO) {
        // The asset index comes first: it lists the asset objects to fetch.
        val indexRef = version.assetIndex
        val indexFile = paths.assetIndex(indexRef.id)
        downloads.downloadAll(listOf(DownloadTask(indexRef.url, indexFile, indexRef.sha1, indexRef.size)))
        val index = DyroxJson.decodeFromString(AssetIndex.serializer(), Files.readString(indexFile))

        val tasks = buildList {
            version.clientDownload?.let { add(DownloadTask(it.url, paths.versionJar(version.jarId), it.sha1, it.size)) }
            for (library in LibraryResolver.resolve(version.libraries, RuleContext(platform))) {
                for (file in listOfNotNull(library.artifact, library.native)) {
                    val url = file.url ?: continue
                    add(DownloadTask(url, paths.library(file.path), file.sha1, file.size))
                }
            }
            version.logging?.file?.let { add(DownloadTask(it.url, paths.logConfig(it.id), it.sha1, it.size)) }
            for (asset in index.objects.values.distinctBy { it.hash }) {
                add(
                    DownloadTask(
                        url = "$ASSET_BASE_URL${asset.hash.substring(0, 2)}/${asset.hash}",
                        target = paths.assetObject(asset.hash),
                        sha1 = asset.hash,
                        size = asset.size,
                        trustExistingIfSizeMatches = true,
                    ),
                )
            }
        }
        downloads.downloadAll(tasks, onProgress)

        val game = InstalledGame.layout(version, paths, platform, index.virtual, index.mapToResources)
        val missing = game.classpath.filterNot(Files::isRegularFile)
        if (missing.isNotEmpty()) {
            throw MissingGameFileException("Missing files with no download source: ${missing.joinToString()}")
        }
        if (index.virtual || index.mapToResources) buildVirtualAssets(index, game)
        game
    }

    /** Legacy versions read assets by name instead of by hash, so copy them into a name-based tree. */
    private fun buildVirtualAssets(index: AssetIndex, game: InstalledGame) {
        for ((name, asset) in index.objects) {
            val target = AtomicFiles.resolveInside(game.gameAssetsDir, name)
            if (Files.isRegularFile(target) && Files.size(target) == asset.size) continue
            target.parent?.let(Files::createDirectories)
            Files.copy(paths.assetObject(asset.hash), target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val ASSET_BASE_URL = "https://resources.download.minecraft.net/"
    }
}
