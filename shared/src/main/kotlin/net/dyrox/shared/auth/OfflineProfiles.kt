package net.dyrox.shared.auth

import java.util.UUID

/** Offline ("cracked-style") profiles: username only, for singleplayer and offline-mode servers. */
object OfflineProfiles {
    private val VALID_NAME = Regex("^[A-Za-z0-9_]{3,16}$")

    /** The UUID a vanilla offline-mode server assigns to [username], so inventories and ops match. */
    fun uuidFor(username: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))

    /** Minecraft's own username rules; servers may kick names outside them. */
    fun isValidName(username: String): Boolean = VALID_NAME.matches(username)
}

/** Minecraft passes UUIDs on the command line without dashes. */
fun UUID.toUndashedString(): String = toString().replace("-", "")
