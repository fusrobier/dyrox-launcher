package net.dyrox.launcher.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dyrox.launcher.core.download.DownloadProgress
import net.dyrox.launcher.core.install.InstalledGame
import net.dyrox.launcher.core.install.NativesExtractor
import net.dyrox.launcher.core.java.JavaRuntimeManager
import net.dyrox.launcher.core.launch.LaunchCommand
import net.dyrox.launcher.core.launch.LaunchIdentity
import net.dyrox.launcher.core.launch.LaunchOptions
import net.dyrox.launcher.core.launch.Resolution
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

sealed interface LoaderSpec {
    data object Vanilla : LoaderSpec

    /** [loaderVersion] null = newest stable Fabric loader. */
    data class Fabric(val loaderVersion: String? = null, val installFabricApi: Boolean = true) : LoaderSpec
}

data class LaunchRequest(
    val gameVersion: String,
    val loader: LoaderSpec,
    val identity: LaunchIdentity,
    val gameDirectory: Path,
    val nativesDirectory: Path,
    val minMemoryMb: Int = 512,
    val maxMemoryMb: Int = 4096,
    val extraJvmArguments: List<String> = emptyList(),
    val resolution: Resolution? = null,
    /** Custom Java executable; null = Mojang's runtime for this version. */
    val javaExecutable: Path? = null,
    val systemProperties: Map<String, String> = emptyMap(),
)

enum class LaunchStage(val label: String) {
    RESOLVING("Resolving version"),
    INSTALLING_LOADER("Installing Fabric loader"),
    DOWNLOADING_GAME("Downloading game files"),
    INSTALLING_JAVA("Installing Java runtime"),
    INSTALLING_MODS("Installing mods"),
    PREPARING("Preparing game directory"),
    READY("Ready"),
}

sealed interface LaunchProgress {
    data class Stage(val stage: LaunchStage, val detail: String = "") : LaunchProgress
    data class Download(val stage: LaunchStage, val progress: DownloadProgress) : LaunchProgress
}

/** Runs every step between "the user pressed Play" and a ready-to-run [LaunchCommand]. */
class GameLauncher(private val core: LauncherCore) {
    /** [storage] decides where game files go: the shared store, or an instance's isolated one. */
    suspend fun prepare(
        request: LaunchRequest,
        storage: InstallServices = core.install,
        onProgress: (LaunchProgress) -> Unit = {},
    ): LaunchCommand =
        withContext(Dispatchers.IO) {
            val platform = core.platform

            val versionId = when (val loader = request.loader) {
                LoaderSpec.Vanilla -> request.gameVersion
                is LoaderSpec.Fabric -> {
                    onProgress(LaunchProgress.Stage(LaunchStage.INSTALLING_LOADER, request.gameVersion))
                    storage.fabric.install(request.gameVersion, loader.loaderVersion)
                }
            }

            onProgress(LaunchProgress.Stage(LaunchStage.RESOLVING, versionId))
            val version = storage.resolver.resolve(versionId)

            onProgress(LaunchProgress.Stage(LaunchStage.DOWNLOADING_GAME, version.id))
            val game = storage.gameInstaller.install(version, platform) {
                onProgress(LaunchProgress.Download(LaunchStage.DOWNLOADING_GAME, it))
            }

            val java = request.javaExecutable ?: run {
                val component = version.javaVersion?.component ?: JavaRuntimeManager.LEGACY_COMPONENT
                onProgress(LaunchProgress.Stage(LaunchStage.INSTALLING_JAVA, component))
                core.java.ensure(component) { onProgress(LaunchProgress.Download(LaunchStage.INSTALLING_JAVA, it)) }
            }

            Files.createDirectories(request.gameDirectory)
            val loader = request.loader
            if (loader is LoaderSpec.Fabric && loader.installFabricApi) {
                onProgress(LaunchProgress.Stage(LaunchStage.INSTALLING_MODS, "Fabric API"))
                core.mods.ensureFabricApi(request.gameDirectory.resolve("mods"), version.gameVersion) {
                    onProgress(LaunchProgress.Download(LaunchStage.INSTALLING_MODS, it))
                }
            }

            onProgress(LaunchProgress.Stage(LaunchStage.PREPARING))
            Files.createDirectories(request.nativesDirectory)
            NativesExtractor.extract(game.nativeArchives, request.nativesDirectory)
            if (game.mapAssetsToResources) copyResources(game, request.gameDirectory)

            val options = LaunchOptions(
                javaExecutable = java,
                gameDirectory = request.gameDirectory,
                nativesDirectory = request.nativesDirectory,
                minMemoryMb = request.minMemoryMb,
                maxMemoryMb = request.maxMemoryMb,
                extraJvmArguments = request.extraJvmArguments,
                systemProperties = request.systemProperties,
                resolution = request.resolution,
            )
            core.commandBuilder.build(game, request.identity, options, platform).also {
                onProgress(LaunchProgress.Stage(LaunchStage.READY))
            }
        }

    /** Pre-1.6 versions read sounds and textures from `<gameDir>/resources`. */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun copyResources(game: InstalledGame, gameDirectory: Path) {
        val source = game.gameAssetsDir
        val target = gameDirectory.resolve("resources")
        source.walk().filter { it.isRegularFile() }.forEach { file ->
            val destination = target.resolve(file.relativeTo(source).toString())
            if (!Files.exists(destination)) {
                destination.parent?.let(Files::createDirectories)
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
