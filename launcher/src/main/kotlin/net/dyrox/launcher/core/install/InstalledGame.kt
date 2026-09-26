package net.dyrox.launcher.core.install

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.dyrox.launcher.core.LauncherPaths
import net.dyrox.launcher.core.library.LibraryResolver
import net.dyrox.launcher.core.rules.RuleContext
import net.dyrox.launcher.core.version.ResolvedVersion
import net.dyrox.shared.platform.Platform
import java.nio.file.Path

@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject> = emptyMap(),
    /** Pre-1.7.3: assets must also be laid out by name under `assets/virtual/<index>`. */
    val virtual: Boolean = false,
    /** Pre-1.6: assets must be copied into `<gameDir>/resources`. */
    @SerialName("map_to_resources") val mapToResources: Boolean = false,
)

@Serializable
data class AssetObject(val hash: String, val size: Long)

/** A legacy natives jar and the entry prefixes to skip when extracting it. */
data class NativeArchive(val jar: Path, val excludes: List<String>)

/** Everything the launch command needs to know about an installed version's files. */
data class InstalledGame(
    val version: ResolvedVersion,
    /** Libraries in declaration order, then the client jar. */
    val classpath: List<Path>,
    val nativeArchives: List<NativeArchive>,
    val clientJar: Path,
    val librariesDir: Path,
    val assetsRoot: Path,
    /** `${game_assets}`: the virtual asset directory for legacy versions, otherwise the assets root. */
    val gameAssetsDir: Path,
    val mapAssetsToResources: Boolean,
    val logConfig: Path?,
) {
    companion object {
        /** Pure: computes file locations without touching the network or disk. */
        fun layout(
            version: ResolvedVersion,
            paths: LauncherPaths,
            platform: Platform,
            virtualAssets: Boolean = false,
            mapAssetsToResources: Boolean = false,
        ): InstalledGame {
            val libraries = LibraryResolver.resolve(version.libraries, RuleContext(platform))
            val clientJar = paths.versionJar(version.jarId)
            // plusElement, not `+`: Path is Iterable<Path>, so `list + path` would append each path segment.
            val classpath = libraries.mapNotNull { it.artifact }.map { paths.library(it.path) }.distinct().plusElement(clientJar)
            val natives = libraries.mapNotNull { lib -> lib.native?.let { NativeArchive(paths.library(it.path), lib.extractExcludes) } }
            val usesVirtualDir = virtualAssets || mapAssetsToResources
            return InstalledGame(
                version = version,
                classpath = classpath,
                nativeArchives = natives,
                clientJar = clientJar,
                librariesDir = paths.librariesDir,
                assetsRoot = paths.assetsDir,
                gameAssetsDir = if (usesVirtualDir) paths.virtualAssets(version.assetIndex.id) else paths.assetsDir,
                mapAssetsToResources = mapAssetsToResources,
                logConfig = version.logging?.let { paths.logConfig(it.file.id) },
            )
        }
    }
}
