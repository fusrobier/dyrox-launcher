package net.dyrox.launcher.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.dyrox.launcher.core.LauncherCore
import net.dyrox.launcher.core.manifest.VersionManifest

/** Minecraft versions (Mojang manifest) and which of them Fabric supports; loaded once, shared by all screens. */
class VersionCatalog(private val core: LauncherCore, private val scope: CoroutineScope) {
    var versions by mutableStateOf<List<VersionManifest.Entry>>(emptyList()); private set
    var latestRelease by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** Null until known (or if Fabric's API is unreachable). */
    var fabricVersions by mutableStateOf<Set<String>?>(null); private set

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                val manifest = core.manifests.manifest()
                versions = manifest.versions
                latestRelease = manifest.latest.release
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                loading = false
            }
        }
        scope.launch {
            fabricVersions = runCatching { core.install.fabric.supportedGameVersions().map { it.version }.toSet() }.getOrNull()
        }
    }

    fun supportsFabric(version: String): Boolean = fabricVersions?.contains(version) ?: true

    fun filtered(query: String, includeSnapshots: Boolean): List<VersionManifest.Entry> {
        val needle = query.trim()
        return versions.filter { (it.isRelease || (includeSnapshots && it.isSnapshot)) && it.id.contains(needle, ignoreCase = true) }
    }
}
