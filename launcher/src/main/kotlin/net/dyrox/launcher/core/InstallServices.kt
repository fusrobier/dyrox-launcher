package net.dyrox.launcher.core

import net.dyrox.launcher.core.download.DownloadManager
import net.dyrox.launcher.core.fabric.FabricInstaller
import net.dyrox.launcher.core.install.GameInstaller
import net.dyrox.launcher.core.manifest.VersionManifestService
import net.dyrox.launcher.core.version.VersionRepository
import net.dyrox.launcher.core.version.VersionResolver
import net.dyrox.shared.http.HttpService

/**
 * Everything that reads or writes game files for one storage location. The launcher has one for the
 * shared store; instances with isolated storage get their own (Java runtimes always stay shared).
 */
class InstallServices(
    val paths: LauncherPaths,
    http: HttpService,
    downloads: DownloadManager,
    manifests: VersionManifestService,
) {
    val versions = VersionRepository(paths, http, manifests)
    val resolver = VersionResolver(versions::load)
    val gameInstaller = GameInstaller(paths, downloads)
    val fabric = FabricInstaller(paths, http)
}
