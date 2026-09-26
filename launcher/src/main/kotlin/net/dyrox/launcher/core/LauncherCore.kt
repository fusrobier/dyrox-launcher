package net.dyrox.launcher.core

import net.dyrox.launcher.LauncherInfo
import net.dyrox.launcher.core.download.DownloadManager
import net.dyrox.launcher.core.fabric.FabricInstaller
import net.dyrox.launcher.core.install.GameInstaller
import net.dyrox.launcher.core.java.JavaRuntimeManager
import net.dyrox.launcher.core.launch.LaunchCommandBuilder
import net.dyrox.launcher.core.manifest.VersionManifestService
import net.dyrox.launcher.core.mods.ModInstaller
import net.dyrox.launcher.core.version.VersionRepository
import net.dyrox.launcher.core.version.VersionResolver
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.platform.Platform
import java.nio.file.Path

/** Composition root: wires the launcher services together once. */
class LauncherCore(
    val paths: LauncherPaths = LauncherPaths.default(),
    val platform: Platform = Platform.current,
    downloadParallelism: Int = 16,
) {
    val http = HttpService(LauncherInfo.userAgent)
    val downloads = DownloadManager(http, downloadParallelism)
    val manifests = VersionManifestService(http, paths.cacheDir.resolve("version_manifest_v2.json"))
    val versions = VersionRepository(paths, http, manifests)
    val resolver = VersionResolver(versions::load)
    val gameInstaller = GameInstaller(paths, downloads)
    val java = JavaRuntimeManager(paths, http, downloads, platform)
    val fabric = FabricInstaller(paths, http)
    val mods = ModInstaller(http, downloads)
    val commandBuilder = LaunchCommandBuilder(LauncherInfo.BRAND, LauncherInfo.version)
    val gameLauncher = GameLauncher(this)

    /** Phase 2 has a single instance; Phase 4 replaces this with real instance management. */
    val defaultInstanceDir: Path get() = paths.instancesDir.resolve("default")
}
