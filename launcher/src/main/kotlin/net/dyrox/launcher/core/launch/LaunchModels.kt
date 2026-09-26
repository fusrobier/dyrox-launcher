package net.dyrox.launcher.core.launch

import net.dyrox.shared.account.AccountType
import net.dyrox.shared.account.GameSession
import net.dyrox.shared.auth.OfflineProfiles
import net.dyrox.shared.auth.toUndashedString
import java.nio.file.Path

/** Who the game runs as. Phase 3 produces these from Microsoft accounts; offline ones are built here. */
data class LaunchIdentity(
    val username: String,
    /** Undashed UUID. */
    val uuid: String,
    val accessToken: String,
    /** `msa` for Microsoft accounts, `legacy` for offline profiles. */
    val userType: String,
    val xuid: String = "0",
    val clientId: String = "0",
) {
    val isOffline: Boolean get() = userType == USER_TYPE_OFFLINE

    override fun toString(): String = "LaunchIdentity(username=$username, uuid=$uuid, userType=$userType)"

    companion object {
        const val USER_TYPE_MSA = "msa"
        const val USER_TYPE_OFFLINE = "legacy"

        /** From a stored account's session (Microsoft or offline). */
        fun from(session: GameSession): LaunchIdentity = LaunchIdentity(
            username = session.username,
            uuid = session.uuid,
            accessToken = session.accessToken,
            userType = if (session.type == AccountType.MICROSOFT) USER_TYPE_MSA else USER_TYPE_OFFLINE,
            xuid = session.xuid ?: "0",
        )

        fun offline(username: String): LaunchIdentity = LaunchIdentity(
            username = username,
            uuid = OfflineProfiles.uuidFor(username).toUndashedString(),
            // Any non-empty value works offline; online servers reject it, which is the point.
            accessToken = "0",
            userType = USER_TYPE_OFFLINE,
        )
    }
}

@kotlinx.serialization.Serializable
data class Resolution(val width: Int, val height: Int)

data class LaunchOptions(
    val javaExecutable: Path,
    val gameDirectory: Path,
    val nativesDirectory: Path,
    val minMemoryMb: Int = 512,
    val maxMemoryMb: Int = 4096,
    /** Appended after the version's JVM arguments, so they can override them. */
    val extraJvmArguments: List<String> = emptyList(),
    val extraGameArguments: List<String> = emptyList(),
    /** `-Dkey=value` pairs, e.g. `dyrox.instance`, read by the Dyrox client mod. */
    val systemProperties: Map<String, String> = emptyMap(),
    val resolution: Resolution? = null,
)

data class LaunchCommand(
    val executable: Path,
    val arguments: List<String>,
    val workingDirectory: Path,
    /** Values that must never appear in logs or the UI (access tokens). */
    val secrets: List<String> = emptyList(),
) {
    val commandLine: List<String> get() = listOf(executable.toString()) + arguments

    fun redact(text: String): String =
        secrets.filter { it.length >= MIN_SECRET_LENGTH }.fold(text) { acc, secret -> acc.replace(secret, "********") }

    fun redactedCommandLine(): List<String> = commandLine.map(::redact)

    override fun toString(): String = "LaunchCommand(${redactedCommandLine().joinToString(" ")})"

    private companion object {
        /** Shorter values (like the offline token "0") would redact random digits. */
        const val MIN_SECRET_LENGTH = 8
    }
}
