package net.dyrox.launcher.core

import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.platform.OperatingSystem
import java.nio.file.Path

/**
 * On-disk layout. Game files (versions, libraries, assets, Java runtimes) live once under [sharedDir]
 * and are shared by every instance; each instance only owns its own game directory.
 */
class LauncherPaths(val root: Path) {
    val sharedDir: Path = root.resolve("shared")
    val versionsDir: Path = sharedDir.resolve("versions")
    val librariesDir: Path = sharedDir.resolve("libraries")
    val assetsDir: Path = sharedDir.resolve("assets")
    val runtimesDir: Path = sharedDir.resolve("runtimes")
    val instancesDir: Path = root.resolve("instances")
    val cacheDir: Path = root.resolve("cache")

    fun versionJson(id: String): Path = versionsDir.resolve(id).resolve("$id.json")
    fun versionJar(id: String): Path = versionsDir.resolve(id).resolve("$id.jar")
    fun library(relativePath: String): Path = AtomicFiles.resolveInside(librariesDir, relativePath)
    fun assetIndex(id: String): Path = assetsDir.resolve("indexes").resolve("$id.json")
    fun virtualAssets(indexId: String): Path = assetsDir.resolve("virtual").resolve(indexId)
    fun logConfig(id: String): Path = AtomicFiles.resolveInside(assetsDir.resolve("log_configs"), id)

    fun assetObject(hash: String): Path {
        require(HASH.matches(hash)) { "Invalid asset hash: $hash" }
        return assetsDir.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash)
    }

    companion object {
        private val HASH = Regex("^[0-9a-f]{40}$")

        /**
         * `%APPDATA%\DyroxLauncher` on Windows, `$XDG_DATA_HOME/dyrox-launcher` on Linux.
         * Override with `-Ddyrox.home=...` or the `DYROX_HOME` environment variable.
         */
        fun default(): LauncherPaths {
            val override = System.getProperty("dyrox.home") ?: System.getenv("DYROX_HOME")
            if (!override.isNullOrBlank()) return LauncherPaths(Path.of(override))

            val home = Path.of(System.getProperty("user.home"))
            val root = when (OperatingSystem.current) {
                OperatingSystem.WINDOWS ->
                    (System.getenv("APPDATA")?.let(Path::of) ?: home.resolve("AppData").resolve("Roaming"))
                        .resolve("DyroxLauncher")
                OperatingSystem.MACOS -> home.resolve("Library").resolve("Application Support").resolve("DyroxLauncher")
                else -> (System.getenv("XDG_DATA_HOME")?.let(Path::of) ?: home.resolve(".local").resolve("share"))
                    .resolve("dyrox-launcher")
            }
            return LauncherPaths(root)
        }
    }
}
