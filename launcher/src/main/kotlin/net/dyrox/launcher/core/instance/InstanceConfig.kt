package net.dyrox.launcher.core.instance

import kotlinx.serialization.Serializable
import net.dyrox.launcher.core.launch.Resolution

@Serializable
enum class LoaderType(val label: String) {
    VANILLA("Vanilla"),
    FABRIC("Fabric"),
}

/** One instance = `instances/<id>/instance.json`. Everything here is per instance; nothing is secret. */
@Serializable
data class InstanceConfig(
    /** Folder name under `instances/`; stable, derived from the first name. */
    val id: String,
    val name: String,
    val gameVersion: String,
    val loader: LoaderType = LoaderType.FABRIC,
    /** Null = newest stable loader. */
    val loaderVersion: String? = null,
    /** Install the Dyrox client (Fabric only, and only for the Minecraft version it's built for). */
    val dyroxClient: Boolean = true,
    /** Account to play with; null = the account selected in the launcher. */
    val accountId: String? = null,
    val minMemoryMb: Int = 512,
    val maxMemoryMb: Int = 4096,
    val jvmArguments: List<String> = emptyList(),
    val resolution: Resolution? = null,
    /** Custom Java executable; null = Mojang's runtime for the version. */
    val javaPath: String? = null,
    /** Custom game directory; null = `instances/<id>/minecraft`. Must not be shared with another instance. */
    val gameDirectory: String? = null,
    /** Own copy of versions/libraries/assets instead of the shared store (uses more disk). */
    val isolatedStorage: Boolean = false,
    /** Dyrox client config profile to load (Phase 5). */
    val configProfile: String = "default",
    val createdAt: Long = 0,
    val lastPlayedAt: Long? = null,
    /** Set on instances created by "launch with selected accounts": the instance they mirror. */
    val linkedTo: String? = null,
    val siblingIndex: Int? = null,
)
