package net.dyrox.client.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.json.DyroxJson
import net.dyrox.shared.json.DyroxJsonPretty
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/** Client-wide state that isn't part of a profile. */
@Serializable
data class ClientState(
    val lastProfile: String = ConfigSystem.DEFAULT_PROFILE,
    val commandPrefix: String = ".",
)

/**
 * Profiles are `profiles/<name>.json`, one JSON object per section (`modules`, later `gui` and `hud`),
 * each mapping configurable names to their settings:
 * ```
 * { "version": 1, "modules": { "Sprint": { "Enabled": true, "Bind": {...} } } }
 * ```
 * Changes are saved automatically (debounced) and on shutdown.
 */
class ConfigSystem(
    private val dir: Path,
    /** Section name → configurables in it. Resolved lazily so modules can register first. */
    private val sections: () -> Map<String, List<Configurable>>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val logger = LoggerFactory.getLogger("Dyrox/Config")
    private val profilesDir: Path = dir.resolve("profiles")
    private val stateFile: Path = dir.resolve("client.json")

    var state: ClientState = loadState()
        private set

    var activeProfile: String = state.lastProfile
        private set

    @Volatile
    private var dirtySince: Long? = null

    /** Suppresses autosave marking while a profile is being applied. */
    @Volatile
    private var loading = false

    /** Call once all configurables exist: marks the config dirty whenever any setting changes. */
    fun trackChanges() {
        sections().values.flatten().flatMap { it.allValues() }.forEach { value ->
            value.onChange { if (!loading) markDirty() }
        }
    }

    fun markDirty() {
        if (dirtySince == null) dirtySince = clock()
    }

    /** Saves if something changed at least [debounceMillis] ago. Call regularly (e.g. every tick). */
    fun saveIfDirty(debounceMillis: Long = 2_000) {
        val since = dirtySince ?: return
        if (clock() - since >= debounceMillis) save()
    }

    fun profileNames(): List<String> {
        if (!Files.isDirectory(profilesDir)) return emptyList()
        return Files.list(profilesDir).use { files ->
            files.map { it.fileName.toString() }.filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.sorted().toList()
        }
    }

    fun exists(profile: String): Boolean = isValidName(profile) && Files.isRegularFile(profileFile(profile))

    /**
     * Loads [profile] and makes it active. A missing profile resets everything to defaults and is
     * created on the next save. Returns problems found (unknown values, bad JSON), empty if clean.
     */
    fun load(profile: String): List<String> {
        require(isValidName(profile)) { "Invalid profile name '$profile' (letters, digits, - and _ only)" }
        val problems = ArrayList<String>()
        val file = profileFile(profile)
        val root: JsonObject? = if (Files.isRegularFile(file)) {
            try {
                DyroxJson.parseToJsonElement(Files.readString(file)).jsonObject
            } catch (e: Exception) {
                problems += "Could not read $profile.json: ${e.message}"
                null
            }
        } else {
            null
        }
        loading = true
        try {
            for ((sectionName, configurables) in sections()) {
                val section = root?.get(sectionName)?.jsonObject
                for (configurable in configurables) {
                    configurable.resetAll()
                    val json = section?.entries?.firstOrNull { it.key.equals(configurable.name, true) }?.value?.jsonObject ?: continue
                    configurable.fromJson(json) { problems += it }
                }
            }
        } finally {
            loading = false
        }
        root?.get("version")?.jsonPrimitive?.int?.let { if (it > VERSION) problems += "$profile.json was written by a newer Dyrox version" }
        activeProfile = profile
        updateState { it.copy(lastProfile = profile) }
        dirtySince = null
        problems.forEach { logger.warn("Config: {}", it) }
        return problems
    }

    /** Writes the current settings to [profile] (default: the active one). */
    fun save(profile: String = activeProfile) {
        require(isValidName(profile)) { "Invalid profile name '$profile'" }
        val root = buildJsonObject {
            put("version", VERSION)
            for ((sectionName, configurables) in sections()) {
                put(sectionName, JsonObject(configurables.associate { it.name to it.toJson() }))
            }
        }
        AtomicFiles.writeString(profileFile(profile), DyroxJsonPretty.encodeToString(JsonObject.serializer(), root))
        if (profile == activeProfile) dirtySince = null
    }

    /** Saves the current settings under a new name and switches to it. */
    fun saveAs(profile: String) {
        save(profile)
        activeProfile = profile
        updateState { it.copy(lastProfile = profile) }
    }

    fun delete(profile: String): Boolean {
        require(isValidName(profile)) { "Invalid profile name '$profile'" }
        require(profile != activeProfile) { "Can't delete the active profile" }
        return Files.deleteIfExists(profileFile(profile))
    }

    fun updateState(transform: (ClientState) -> ClientState) {
        state = transform(state)
        AtomicFiles.writeString(stateFile, DyroxJsonPretty.encodeToString(ClientState.serializer(), state))
    }

    private fun profileFile(profile: String): Path = profilesDir.resolve("$profile.json")

    private fun loadState(): ClientState = runCatching {
        DyroxJson.decodeFromString(ClientState.serializer(), Files.readString(stateFile))
    }.getOrDefault(ClientState())

    companion object {
        const val VERSION = 1
        const val DEFAULT_PROFILE = "default"
        private val NAME = Regex("^[A-Za-z0-9_-]{1,32}$")

        fun isValidName(name: String): Boolean = NAME.matches(name)
    }
}
