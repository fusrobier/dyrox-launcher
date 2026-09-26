package net.dyrox.shared.ipc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.dyrox.shared.account.AccountType

/**
 * Launcher ⇄ game messages: one JSON object per line over a loopback TCP socket.
 * The game learns the port and its per-instance token from environment variables (never argv),
 * and must send [Hello] with that token first; anything else closes the connection.
 */
@Serializable
sealed interface IpcMessage {
    // --- game → launcher ---

    @Serializable
    @SerialName("hello")
    data class Hello(val token: String, val pid: Long, val clientVersion: String? = null) : IpcMessage {
        override fun toString(): String = "Hello(pid=$pid, clientVersion=$clientVersion)"
    }

    /** Free-form progress, e.g. `title_screen`, `in_world`. */
    @Serializable
    @SerialName("status")
    data class Status(val state: String, val detail: String? = null) : IpcMessage

    /** In-game alt manager: "give me a ready session for this account". */
    @Serializable
    @SerialName("session_request")
    data class SessionRequest(val requestId: Int, val accountId: String) : IpcMessage

    @Serializable
    @SerialName("accounts_request")
    data class AccountsRequest(val requestId: Int) : IpcMessage

    // --- launcher → game ---

    @Serializable
    @SerialName("welcome")
    data class Welcome(val instanceId: String, val instanceName: String) : IpcMessage

    @Serializable
    @SerialName("session_response")
    data class SessionResponse(val requestId: Int, val session: IpcSession? = null, val error: String? = null) : IpcMessage

    @Serializable
    @SerialName("accounts_response")
    data class AccountsResponse(val requestId: Int, val accounts: List<IpcAccount>) : IpcMessage

    /** Asks the game to close cleanly (saving worlds), like pressing "Quit Game". */
    @Serializable
    @SerialName("shutdown")
    data object Shutdown : IpcMessage
}

@Serializable
data class IpcSession(
    val accountId: String,
    val username: String,
    val uuid: String,
    val accessToken: String,
    val xuid: String? = null,
    val type: AccountType,
) {
    override fun toString(): String = "IpcSession(username=$username, type=$type)"
}

@Serializable
data class IpcAccount(
    val id: String,
    val username: String,
    val uuid: String,
    val type: AccountType,
    /** Already playing in another running instance. */
    val inUse: Boolean = false,
)

/** Environment variables the launcher sets on each game process. */
object IpcEnvironment {
    const val PORT = "DYROX_IPC_PORT"
    const val TOKEN = "DYROX_IPC_TOKEN"
    const val INSTANCE_ID = "DYROX_INSTANCE_ID"
}

object IpcCodec {
    /** Lines longer than this are rejected, so a misbehaving peer can't exhaust memory. */
    const val MAX_LINE_CHARS = 1 shl 20

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(message: IpcMessage): String = json.encodeToString(IpcMessage.serializer(), message)

    /** Null for malformed or unknown messages (newer peers may send types we don't know yet). */
    fun decode(line: String): IpcMessage? = try {
        json.decodeFromString(IpcMessage.serializer(), line)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Reads one `\n`-terminated line, or null at end of stream. Throws if a line exceeds [MAX_LINE_CHARS]. */
    internal fun readLine(reader: java.io.Reader): String? {
        val builder = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c < 0) return if (builder.isEmpty()) null else builder.toString()
            if (c == '\n'.code) return builder.toString().trimEnd('\r')
            if (builder.length >= MAX_LINE_CHARS) throw java.io.IOException("IPC line too long")
            builder.append(c.toChar())
        }
    }
}
