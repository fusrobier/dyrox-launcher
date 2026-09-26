package net.dyrox.launcher.ui.instances

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.core.instance.InstanceConfig
import net.dyrox.launcher.core.instance.InstanceSession
import net.dyrox.launcher.core.instance.LoaderType
import net.dyrox.launcher.ui.accounts.AccountsViewModel
import net.dyrox.launcher.ui.accounts.Banner
import net.dyrox.launcher.ui.common.VersionCatalog
import net.dyrox.launcher.ui.platform.DesktopActions

enum class InstanceTab(val label: String) { CONSOLE("Console"), SETTINGS("Settings") }

sealed interface InstanceDialog {
    data object NewInstance : InstanceDialog
    data class ChooseVersion(val current: String, val onChosen: (String) -> Unit) : InstanceDialog
    data class ConfirmDelete(val instance: InstanceConfig) : InstanceDialog
    data class MultiLaunch(val templateId: String) : InstanceDialog
}

class InstancesViewModel(
    val core: LauncherCore,
    val accounts: AccountsViewModel,
    val catalog: VersionCatalog,
    private val scope: CoroutineScope,
) {
    var instances by mutableStateOf<List<InstanceConfig>>(emptyList()); private set
    var sessions by mutableStateOf<Map<String, InstanceSession>>(emptyMap()); private set
    var selectedId by mutableStateOf<String?>(null)
    var tab by mutableStateOf(InstanceTab.CONSOLE)
    var dialog by mutableStateOf<InstanceDialog?>(null)
    var banner by mutableStateOf<Banner?>(null); private set
    private var bannerJob: Job? = null

    val selected: InstanceConfig? get() = instances.firstOrNull { it.id == selectedId } ?: instances.firstOrNull()

    init {
        scope.launch { core.instances.instances.collect { instances = it } }
        scope.launch { core.supervisor.sessions.collect { sessions = it } }
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { core.instances.load() }
            if (loaded.isEmpty()) {
                // First run: one ready-to-play instance on the newest release.
                val version = runCatching { core.manifests.manifest().latest.release }.getOrDefault("26.3")
                withContext(Dispatchers.IO) { core.instances.create("Main", version, LoaderType.FABRIC) }
            }
        }
    }

    fun session(instance: InstanceConfig): InstanceSession? = sessions[instance.id]

    fun select(instance: InstanceConfig) {
        selectedId = instance.id
    }

    fun play(instance: InstanceConfig) = action {
        core.supervisor.launch(instance.id)
        selectedId = instance.id
        tab = InstanceTab.CONSOLE
    }

    fun stop(instance: InstanceConfig) = core.supervisor.stop(instance.id)

    fun kill(instance: InstanceConfig) = core.supervisor.kill(instance.id)

    fun create(name: String, version: String, loader: LoaderType) = action {
        val created = withContext(Dispatchers.IO) { core.instances.create(name, version, loader) }
        dialog = null
        selectedId = created.id
        tab = InstanceTab.SETTINGS
        show("Created ${created.name}")
    }

    /** Returns an error message, or null when saved. */
    fun save(instance: InstanceConfig): String? = try {
        core.instances.save(instance)
        show("Saved ${instance.name}")
        null
    } catch (e: IllegalArgumentException) {
        e.message
    }

    fun duplicate(instance: InstanceConfig) = action {
        val copy = withContext(Dispatchers.IO) { core.instances.duplicate(instance.id) }
        selectedId = copy.id
        show("Created ${copy.name}")
    }

    fun delete(instance: InstanceConfig) = action {
        if (session(instance)?.isActive == true) throw IllegalStateException("Stop ${instance.name} before deleting it")
        withContext(Dispatchers.IO) { core.instances.delete(instance.id) }
        dialog = null
        if (selectedId == instance.id) selectedId = null
        show("Deleted ${instance.name}")
    }

    fun openFolder(instance: InstanceConfig) {
        DesktopActions.openFolder(core.instances.gameDirectory(instance))
    }

    fun launchWithAccounts(templateId: String, accountIds: List<String>) = action {
        dialog = null
        val started = core.supervisor.launchWithAccounts(templateId, accountIds)
        show("Starting ${started.size} instance(s): " + started.joinToString { "${it.instance.name} as ${it.account.username}" })
    }

    fun show(text: String, isError: Boolean = false) {
        banner = Banner(text, isError)
        bannerJob?.cancel()
        bannerJob = scope.launch {
            delay(if (isError) 8000 else 4000)
            banner = null
        }
    }

    private fun action(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show(e.message ?: e.toString(), isError = true)
            }
        }
    }
}
