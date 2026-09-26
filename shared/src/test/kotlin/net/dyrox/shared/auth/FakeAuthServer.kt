package net.dyrox.shared.auth

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stands in for login.microsoftonline.com, Xbox Live and Minecraft services. Each route answers from a
 * queue of scripted responses (the last one repeats) and every request is recorded for assertions.
 */
class FakeAuthServer : AutoCloseable {
    data class Recorded(val method: String, val path: String, val body: String, val headers: Map<String, String>)

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val routes = ConcurrentHashMap<String, ConcurrentLinkedDeque<Pair<Int, String>>>()
    val requests = CopyOnWriteArrayList<Recorded>()

    val base = "http://127.0.0.1:${server.address.port}"
    val endpoints = AuthEndpoints(
        microsoftAuthority = "$base/ms",
        xboxUserAuthenticate = "$base/xbl",
        xstsAuthorize = "$base/xsts",
        minecraftServices = "$base/mc",
    )

    init {
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val key = "${exchange.requestMethod} ${exchange.requestURI.path}"
            requests += Recorded(
                exchange.requestMethod,
                exchange.requestURI.path,
                body,
                exchange.requestHeaders.mapValues { it.value.joinToString(",") }.mapKeys { it.key.lowercase() },
            )
            val queue = routes[key]
            val (status, response) = when {
                queue == null -> 404 to """{"error":"no route $key"}"""
                queue.size > 1 -> queue.poll()
                else -> queue.peek()
            }
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    fun on(method: String, path: String, vararg responses: Pair<Int, String>) {
        routes["$method $path"] = ConcurrentLinkedDeque(responses.toList())
    }

    fun requestsTo(path: String) = requests.filter { it.path == path }

    /** Scripts a complete, successful chain. */
    fun happyPath(mcToken: String = minecraftToken("2535400000000001"), refreshToken: String = "refresh-1") {
        on("POST", "/ms/devicecode", 200 to """{"device_code":"dev-code","user_code":"ABCD-1234","verification_uri":"https://www.microsoft.com/link","expires_in":900,"interval":5}""")
        on("POST", "/ms/token", 200 to """{"access_token":"ms-access","refresh_token":"$refreshToken","expires_in":3600,"token_type":"Bearer"}""")
        on("POST", "/xbl", 200 to """{"IssueInstant":"2026-09-26T00:00:00Z","NotAfter":"2026-10-10T00:00:00Z","Token":"xbl-token","DisplayClaims":{"xui":[{"uhs":"user-hash"}]}}""")
        on("POST", "/xsts", 200 to """{"IssueInstant":"2026-09-26T00:00:00Z","NotAfter":"2026-09-27T00:00:00Z","Token":"xsts-token","DisplayClaims":{"xui":[{"uhs":"user-hash"}]}}""")
        on("POST", "/mc/authentication/login_with_xbox", 200 to """{"username":"a1b2","roles":[],"access_token":"$mcToken","token_type":"Bearer","expires_in":86400}""")
        on("GET", "/mc/minecraft/profile", 200 to PROFILE_JSON)
    }

    override fun close() = server.stop(0)

    companion object {
        const val PROFILE_JSON =
            """{"id":"069a79f444e94726a5befca90e38aaf5","name":"Notch","skins":[{"id":"s1","state":"ACTIVE","url":"http://textures.minecraft.net/texture/abc","variant":"CLASSIC"}],"capes":[]}"""

        /** An unsigned JWT shaped like a Minecraft access token. */
        fun minecraftToken(xuid: String): String {
            val encoder = Base64.getUrlEncoder().withoutPadding()
            val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
            val payload = encoder.encodeToString("""{"xuid":"$xuid","sub":"x"}""".toByteArray())
            return "$header.$payload.signature"
        }
    }
}
