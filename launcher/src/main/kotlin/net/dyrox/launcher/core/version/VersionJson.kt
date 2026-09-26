package net.dyrox.launcher.core.version

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A version JSON: Mojang's `<id>.json`, or a mod-loader profile (Fabric) that points at its parent
 * through [inheritsFrom]. Every field is optional because profiles only carry what they change.
 */
@Serializable
data class VersionJson(
    val id: String,
    val inheritsFrom: String? = null,
    val type: String? = null,
    val mainClass: String? = null,
    /** Pre-1.13 game arguments as one space-separated string. */
    val minecraftArguments: String? = null,
    /** 1.13+ structured arguments with per-argument rules. */
    val arguments: Arguments? = null,
    val assetIndex: AssetIndexRef? = null,
    val assets: String? = null,
    val downloads: Downloads? = null,
    val libraries: List<Library> = emptyList(),
    val javaVersion: JavaVersionRef? = null,
    val logging: Logging? = null,
    val releaseTime: String? = null,
)

@Serializable
data class Arguments(
    val game: List<Argument> = emptyList(),
    val jvm: List<Argument> = emptyList(),
)

/**
 * One argument entry. In JSON it is either a plain string or `{"rules": [...], "value": "x" | ["x", "y"]}`;
 * both shapes are normalised to this class by [ArgumentSerializer].
 */
@Serializable(with = ArgumentSerializer::class)
data class Argument(
    val values: List<String>,
    val rules: List<Rule> = emptyList(),
)

@Serializable
data class Rule(
    val action: Action,
    val os: OsCondition? = null,
    val features: Map<String, Boolean>? = null,
) {
    @Serializable
    enum class Action {
        @SerialName("allow") ALLOW,
        @SerialName("disallow") DISALLOW,
    }
}

@Serializable
data class OsCondition(
    val name: String? = null,
    /** A regular expression matched against `os.version`. */
    val version: String? = null,
    val arch: String? = null,
)

@Serializable
data class Library(
    /** Maven notation, `group:artifact:version[:classifier][@extension]`. */
    val name: String,
    val downloads: LibraryDownloads? = null,
    /** Maven repository base URL (Fabric-style libraries have no `downloads` block). */
    val url: String? = null,
    val sha1: String? = null,
    val size: Long? = null,
    val rules: List<Rule> = emptyList(),
    /** Legacy (pre-1.19) natives: OS name → classifier, e.g. `"windows": "natives-windows-${arch}"`. */
    val natives: Map<String, String>? = null,
    val extract: Extract? = null,
)

@Serializable
data class LibraryDownloads(
    val artifact: Artifact? = null,
    val classifiers: Map<String, Artifact>? = null,
)

@Serializable
data class Artifact(
    val path: String? = null,
    val sha1: String? = null,
    val size: Long? = null,
    val url: String = "",
)

@Serializable
data class Extract(val exclude: List<String> = emptyList())

@Serializable
data class Downloads(val client: DownloadRef? = null)

@Serializable
data class DownloadRef(
    val sha1: String,
    val size: Long? = null,
    val url: String,
)

@Serializable
data class AssetIndexRef(
    val id: String,
    val sha1: String,
    val size: Long? = null,
    val totalSize: Long? = null,
    val url: String,
)

@Serializable
data class JavaVersionRef(
    val component: String,
    val majorVersion: Int,
)

@Serializable
data class Logging(val client: LoggingEntry? = null)

@Serializable
data class LoggingEntry(
    /** e.g. `-Dlog4j.configurationFile=${path}` */
    val argument: String,
    val file: LoggingFile,
    val type: String? = null,
)

@Serializable
data class LoggingFile(
    val id: String,
    val sha1: String,
    val size: Long? = null,
    val url: String,
)
