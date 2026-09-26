package net.dyrox.client.combat

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.dyrox.shared.io.AtomicFiles
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListSet

/**
 * Players that combat modules never target. Stored once for all profiles in `friends.json`,
 * case-insensitive (Minecraft names are).
 */
object Friends {
    private val logger = LoggerFactory.getLogger("Dyrox/Friends")
    private val json = Json { prettyPrint = true }
    private val names = ConcurrentSkipListSet(String.CASE_INSENSITIVE_ORDER)
    private var file: Path? = null

    val all: List<String> get() = names.toList()

    fun load(file: Path) {
        this.file = file
        names.clear()
        if (!Files.isRegularFile(file)) return
        runCatching { names += json.decodeFromString(ListSerializer(String.serializer()), Files.readString(file)) }
            .onFailure { logger.warn("Could not read {}: {}", file, it.message) }
    }

    fun isFriend(name: String): Boolean = name in names

    /** @return false if [name] was already a friend. */
    fun add(name: String): Boolean = names.add(name).also { if (it) save() }

    fun remove(name: String): Boolean = names.remove(name).also { if (it) save() }

    fun clear() {
        names.clear()
        save()
    }

    private fun save() {
        val target = file ?: return
        runCatching { AtomicFiles.writeString(target, json.encodeToString(ListSerializer(String.serializer()), names.toList())) }
            .onFailure { logger.error("Could not save friends", it) }
    }
}
