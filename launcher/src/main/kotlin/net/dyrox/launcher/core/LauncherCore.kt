package net.dyrox.launcher.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.dyrox.launcher.LauncherInfo
import net.dyrox.launcher.core.accounts.SkinCache
import net.dyrox.launcher.core.download.DownloadManager
import net.dyrox.launcher.core.instance.InstanceRepository
import net.dyrox.launcher.core.instance.InstanceSupervisor
import net.dyrox.launcher.core.instance.LaunchPreparer
import net.dyrox.launcher.core.instance.LoaderType
import net.dyrox.launcher.core.java.JavaRuntimeManager
import net.dyrox.launcher.core.launch.LaunchCommandBuilder
import net.dyrox.launcher.core.manifest.VersionManifestService
import net.dyrox.launcher.core.mods.ModInstaller
import net.dyrox.launcher.core.settings.LauncherSettingsStore
import net.dyrox.shared.account.AccountManager
import net.dyrox.shared.auth.MicrosoftAuthenticator
import net.dyrox.shared.http.HttpService
import net.dyrox.shared.platform.Platform
import net.dyrox.shared.vault.AccountVault
import net.dyrox.shared.vault.MasterKeyStores
import java.nio.file.Path

/** Composition root: wires the launcher services together once. */
class LauncherCore(
    val paths: LauncherPaths = LauncherPaths.default(),
    val platform: Platform = Platform.current,
    downloadParallelism: Int = 16,
) {
    val http = HttpService(LauncherInfo.userAgent)
    val settings = LauncherSettingsStore(paths.root.resolve("launcher.json"))
    val downloads = DownloadManager(http, downloadParallelism)
    val manifests = VersionManifestService(http, paths.cacheDir.resolve("version_manifest_v2.json"))
    /** Game files in the shared store. */
    val install = InstallServices(paths, http, downloads, manifests)
    /** Java runtimes are always shared, even for instances with isolated storage. */
    val java = JavaRuntimeManager(paths, http, downloads, platform)
    val mods = ModInstaller(http, downloads)
    val commandBuilder = LaunchCommandBuilder(LauncherInfo.BRAND, LauncherInfo.version)
    val gameLauncher = GameLauncher(this)

    val accounts = AccountManager(
        vault = AccountVault(paths.root.resolve("accounts.vault"), MasterKeyStores.forPlatform(paths.root)),
        loginService = ::microsoftAuthenticator,
    )
    val skins = SkinCache(http, paths.cacheDir.resolve("skins"))

    val instances = InstanceRepository(paths)

    /** Outlives any UI screen: running games keep being supervised while the user navigates. */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val supervisor = InstanceSupervisor(instances, accounts, LaunchPreparer(::prepareInstance), backgroundScope)

    @Volatile
    private var authenticator: MicrosoftAuthenticator? = null

    /** Null while no Azure client ID is configured. Rebuilt when the ID changes in Settings. */
    fun microsoftAuthenticator(): MicrosoftAuthenticator? {
        val clientId = settings.effectiveClientId ?: return null
        authenticator?.takeIf { it.clientId == clientId }?.let { return it }
        return MicrosoftAuthenticator(http, clientId).also { authenticator = it }
    }

    /** Used by the dev CLI's plain `launch` command. */
    val defaultInstanceDir: Path get() = paths.instancesDir.resolve("default")

    private suspend fun prepareInstance(
        instance: net.dyrox.launcher.core.instance.InstanceConfig,
        identity: net.dyrox.launcher.core.launch.LaunchIdentity,
        systemProperties: Map<String, String>,
        onProgress: (LaunchProgress) -> Unit,
    ): net.dyrox.launcher.core.launch.LaunchCommand {
        val storage = instances.isolatedStorage(instance)?.let { InstallServices(it, http, downloads, manifests) } ?: install
        val request = LaunchRequest(
            gameVersion = instance.gameVersion,
            loader = when (instance.loader) {
                LoaderType.VANILLA -> LoaderSpec.Vanilla
                LoaderType.FABRIC -> LoaderSpec.Fabric(instance.loaderVersion)
            },
            identity = identity,
            gameDirectory = instances.gameDirectory(instance),
            nativesDirectory = instances.nativesDirectory(instance),
            minMemoryMb = instance.minMemoryMb,
            maxMemoryMb = instance.maxMemoryMb,
            extraJvmArguments = instance.jvmArguments,
            resolution = instance.resolution,
            javaExecutable = instance.javaPath?.takeIf { it.isNotBlank() }?.let(Path::of),
            systemProperties = systemProperties,
        )
        return gameLauncher.prepare(request, storage, onProgress)
    }
}
