package net.dyrox.launcher.core.manifest

import kotlinx.serialization.Serializable

/** Mojang's `version_manifest_v2.json`: every published game version and where its JSON lives. */
@Serializable
data class VersionManifest(
    val latest: Latest,
    val versions: List<Entry>,
) {
    @Serializable
    data class Latest(val release: String, val snapshot: String)

    @Serializable
    data class Entry(
        val id: String,
        val type: String,
        val url: String,
        val time: String = "",
        val releaseTime: String = "",
        val sha1: String? = null,
        val complianceLevel: Int = 0,
    ) {
        val isRelease: Boolean get() = type == "release"
        val isSnapshot: Boolean get() = type == "snapshot"
    }

    fun find(id: String): Entry? = versions.firstOrNull { it.id == id }
}
