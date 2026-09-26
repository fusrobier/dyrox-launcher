package net.dyrox.launcher.core.version

/** A version with its whole `inheritsFrom` chain merged into one launchable description. */
data class ResolvedVersion(
    /** Id of the requested (leaf) version, e.g. `fabric-loader-0.19.5-26.3`. Used for `${version_name}`. */
    val id: String,
    /** Id of the root (vanilla) version, e.g. `26.3`. */
    val gameVersion: String,
    /** Id of the version whose client jar is used. */
    val jarId: String,
    val type: String,
    val mainClass: String,
    /** Leaf libraries first; parent libraries overridden by a child are already removed. */
    val libraries: List<Library>,
    val jvmArguments: List<Argument>,
    val gameArguments: List<Argument>,
    /** Pre-1.13 argument string, if any version in the chain uses the legacy format. */
    val legacyGameArguments: String?,
    val assetIndex: AssetIndexRef,
    val clientDownload: DownloadRef?,
    val javaVersion: JavaVersionRef?,
    val logging: LoggingEntry?,
) {
    /**
     * Pre-1.13 versions give no JVM arguments, so the launcher supplies the defaults (natives path, classpath).
     * Structured arguments added by a child profile are appended after them.
     */
    val usesLegacyArguments: Boolean get() = legacyGameArguments != null
}
