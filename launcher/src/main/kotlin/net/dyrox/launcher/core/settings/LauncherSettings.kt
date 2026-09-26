package net.dyrox.launcher.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.json.DyroxJson
import net.dyrox.shared.json.DyroxJsonPretty
import java.nio.file.Files
import java.nio.file.Path

/** Global launcher settings (`launcher.json`). Not secret: nothing sensitive may go in here. */
@Serializable
data class LauncherSettings(
    /** Azure app (client) ID used for Microsoft sign-in. Empty = Microsoft sign-in disabled. */
    val microsoftClientId: String = "",
)

enum class ClientIdSource { NONE, SETTINGS, ENVIRONMENT }

class LauncherSettingsStore(private val file: Path) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    /** `DYROX_MS_CLIENT_ID` / `-Ddyrox.msClientId` override the saved value (handy for CI and dev builds). */
    private val clientIdOverride: String? =
        (System.getProperty("dyrox.msClientId") ?: System.getenv("DYROX_MS_CLIENT_ID"))?.trim()?.takeIf { it.isNotEmpty() }

    val clientIdSource: ClientIdSource
        get() = when {
            clientIdOverride != null -> ClientIdSource.ENVIRONMENT
            _settings.value.microsoftClientId.isNotBlank() -> ClientIdSource.SETTINGS
            else -> ClientIdSource.NONE
        }

    val effectiveClientId: String?
        get() = clientIdOverride ?: _settings.value.microsoftClientId.trim().takeIf { it.isNotEmpty() }

    @Synchronized
    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val updated = transform(_settings.value)
        AtomicFiles.writeString(file, DyroxJsonPretty.encodeToString(LauncherSettings.serializer(), updated))
        _settings.value = updated
    }

    private fun load(): LauncherSettings {
        if (!Files.isRegularFile(file)) return LauncherSettings()
        return runCatching { DyroxJson.decodeFromString(LauncherSettings.serializer(), Files.readString(file)) }
            .getOrDefault(LauncherSettings())
    }

    companion object {
        /** Azure application IDs are GUIDs. */
        private val GUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun isValidClientId(value: String): Boolean = GUID.matches(value.trim())
    }
}
