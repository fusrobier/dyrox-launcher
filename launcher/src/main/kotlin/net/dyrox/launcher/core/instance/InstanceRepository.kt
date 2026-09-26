package net.dyrox.launcher.core.instance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.dyrox.launcher.core.LauncherPaths
import net.dyrox.shared.io.AtomicFiles
import net.dyrox.shared.json.DyroxJson
import net.dyrox.shared.json.DyroxJsonPretty
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.deleteRecursively

class InstanceConflictException(message: String) : IllegalArgumentException(message)

/**
 * Instances on disk. Guarantees that no two instances share a game directory, so `options.txt`,
 * saves, logs and screenshots can never be written by two games at once.
 */
class InstanceRepository(
    private val paths: LauncherPaths,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Moves a folder to the OS trash; returns false if unsupported (then it's deleted). */
    private val moveToTrash: (Path) -> Boolean = ::moveToSystemTrash,
) {
    private val _instances = MutableStateFlow<List<InstanceConfig>>(emptyList())
    val instances: StateFlow<List<InstanceConfig>> = _instances.asStateFlow()

    fun instanceDir(id: String): Path = paths.instancesDir.resolve(id)

    fun gameDirectory(instance: InstanceConfig): Path =
        (instance.gameDirectory?.let(Path::of) ?: instanceDir(instance.id).resolve("minecraft")).toAbsolutePath().normalize()

    fun nativesDirectory(instance: InstanceConfig): Path = instanceDir(instance.id).resolve("natives")

    /** Storage for an instance with [InstanceConfig.isolatedStorage]; null = the shared store. */
    fun isolatedStorage(instance: InstanceConfig): LauncherPaths? =
        if (instance.isolatedStorage) LauncherPaths(paths.root, sharedDir = instanceDir(instance.id).resolve("storage")) else null

    fun find(id: String): InstanceConfig? = _instances.value.firstOrNull { it.id == id }

    @Synchronized
    fun load(): List<InstanceConfig> {
        Files.createDirectories(paths.instancesDir)
        migrateLegacyDefault()
        val loaded = Files.list(paths.instancesDir).use { dirs ->
            dirs.filter { Files.isRegularFile(it.resolve(CONFIG_FILE)) }.toList()
        }.mapNotNull { dir ->
            runCatching {
                DyroxJson.decodeFromString(InstanceConfig.serializer(), Files.readString(dir.resolve(CONFIG_FILE)))
                    .copy(id = dir.fileName.toString()) // the folder name is authoritative
            }.getOrNull()
        }
        _instances.value = loaded.sortedWith(ORDER)
        return _instances.value
    }

    @Synchronized
    fun create(name: String, gameVersion: String, loader: LoaderType): InstanceConfig {
        val instance = InstanceConfig(
            id = uniqueId(name),
            name = name.trim().ifEmpty { "Instance" },
            gameVersion = gameVersion,
            loader = loader,
            createdAt = clock(),
        )
        return write(instance)
    }

    /** Saves [instance] after checking its game directory doesn't collide with another instance's. */
    @Synchronized
    fun save(instance: InstanceConfig): InstanceConfig {
        require(instance.name.isNotBlank()) { "The instance needs a name" }
        require(instance.maxMemoryMb >= instance.minMemoryMb) { "Maximum memory must be at least the minimum" }
        val gameDir = gameDirectory(instance)
        _instances.value.filter { it.id != instance.id }.forEach { other ->
            val otherDir = gameDirectory(other)
            if (gameDir.startsWith(otherDir) || otherDir.startsWith(gameDir)) {
                throw InstanceConflictException("\"${other.name}\" already uses the game folder $otherDir")
            }
        }
        return write(instance)
    }

    @Synchronized
    fun duplicate(id: String): InstanceConfig {
        val source = requireNotNull(find(id)) { "Unknown instance $id" }
        val copy = source.copy(
            id = uniqueId("${source.name} copy"),
            name = "${source.name} (copy)",
            gameDirectory = null,
            accountId = null,
            createdAt = clock(),
            lastPlayedAt = null,
            linkedTo = null,
            siblingIndex = null,
        )
        copyPlayerSettings(gameDirectory(source), gameDirectory(copy))
        return write(copy)
    }

    /**
     * The [index]th sibling of [template] (2, 3, ...) for multi-account launches: reused if it exists,
     * created otherwise. Siblings follow the template's version, loader, memory and JVM settings, but
     * always have their own game directory.
     */
    @Synchronized
    fun siblingFor(template: InstanceConfig, index: Int): InstanceConfig {
        require(index >= 2) { "Sibling indices start at 2" }
        val existing = _instances.value.firstOrNull { it.linkedTo == template.id && it.siblingIndex == index }
        val synced = (existing ?: InstanceConfig(
            id = uniqueId("${template.id}-$index"),
            name = "${template.name} #$index",
            gameVersion = template.gameVersion,
            createdAt = clock(),
            linkedTo = template.id,
            siblingIndex = index,
        )).copy(
            gameVersion = template.gameVersion,
            loader = template.loader,
            loaderVersion = template.loaderVersion,
            dyroxClient = template.dyroxClient,
            minMemoryMb = template.minMemoryMb,
            maxMemoryMb = template.maxMemoryMb,
            jvmArguments = template.jvmArguments,
            resolution = template.resolution,
            javaPath = template.javaPath,
            isolatedStorage = template.isolatedStorage,
            configProfile = template.configProfile,
            gameDirectory = null,
        )
        if (existing == null) copyPlayerSettings(gameDirectory(template), gameDirectory(synced))
        return write(synced)
    }

    @Synchronized
    fun markPlayed(id: String) {
        find(id)?.let { write(it.copy(lastPlayedAt = clock())) }
    }

    /**
     * Removes `instances/<id>` (to the OS trash where possible). A custom game directory outside the
     * instance folder is left untouched.
     */
    @OptIn(ExperimentalPathApi::class)
    @Synchronized
    fun delete(id: String) {
        val dir = instanceDir(id)
        if (Files.exists(dir) && !moveToTrash(dir)) dir.deleteRecursively()
        _instances.value = _instances.value.filterNot { it.id == id }
    }

    private fun write(instance: InstanceConfig): InstanceConfig {
        val dir = instanceDir(instance.id)
        Files.createDirectories(dir)
        AtomicFiles.writeString(dir.resolve(CONFIG_FILE), DyroxJsonPretty.encodeToString(InstanceConfig.serializer(), instance))
        _instances.value = (_instances.value.filterNot { it.id == instance.id } + instance).sortedWith(ORDER)
        return instance
    }

    /** Folder-safe, unique id from a display name: "PvP Main!" → "pvp-main", then "pvp-main-2", ... */
    private fun uniqueId(name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "instance" }
        var candidate = base
        var n = 2
        while (Files.exists(instanceDir(candidate)) || find(candidate) != null) candidate = "$base-${n++}"
        return candidate
    }

    /** Phases 2–3 used `instances/default/` without an instance.json; adopt it as "Default". */
    private fun migrateLegacyDefault() {
        val dir = instanceDir("default")
        if (Files.isDirectory(dir) && !Files.exists(dir.resolve(CONFIG_FILE))) {
            val legacy = InstanceConfig(id = "default", name = "Default", gameVersion = DEFAULT_VERSION, createdAt = clock())
            AtomicFiles.writeString(dir.resolve(CONFIG_FILE), DyroxJsonPretty.encodeToString(InstanceConfig.serializer(), legacy))
        }
    }

    /** Copies the files that make a new instance feel like the old one: keybinds, video settings, server list, mods, mod configs. */
    @OptIn(ExperimentalPathApi::class)
    private fun copyPlayerSettings(from: Path, to: Path) {
        if (!Files.isDirectory(from)) return
        Files.createDirectories(to)
        for (file in listOf("options.txt", "servers.dat")) {
            val source = from.resolve(file)
            if (Files.isRegularFile(source)) Files.copy(source, to.resolve(file), StandardCopyOption.REPLACE_EXISTING)
        }
        for (folder in listOf("config", "mods")) {
            val source = from.resolve(folder)
            if (Files.isDirectory(source)) source.copyToRecursively(to.resolve(folder), followLinks = false, overwrite = true)
        }
    }

    companion object {
        const val CONFIG_FILE = "instance.json"
        const val DEFAULT_VERSION = "26.3"
        private val ORDER = compareBy<InstanceConfig>({ it.linkedTo ?: it.id }, { it.siblingIndex ?: 0 })

        fun moveToSystemTrash(path: Path): Boolean = runCatching {
            val desktop = java.awt.Desktop.getDesktop()
            java.awt.Desktop.isDesktopSupported() && desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH) &&
                desktop.moveToTrash(path.toFile())
        }.getOrDefault(false)
    }
}
