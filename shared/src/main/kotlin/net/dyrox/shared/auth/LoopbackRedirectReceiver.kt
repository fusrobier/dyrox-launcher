package net.dyrox.shared.auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder

/**
 * Receives the OAuth redirect of the browser flow on `http://localhost:<random port>` (RFC 8252 loopback
 * redirect). Register `http://localhost` as a "Mobile and desktop applications" redirect URI in Azure;
 * Microsoft ignores the port for loopback addresses.
 */
class LoopbackRedirectReceiver : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val result = CompletableDeferred<Map<String, String>>()

    val redirectUri: String = "http://localhost:${server.address.port}"

    init {
        server.createContext("/") { exchange -> handle(exchange) }
        server.start()
    }

    /** Suspends until the browser is redirected back; returns the query parameters (`code`, `state` or `error`). */
    suspend fun awaitRedirect(): Map<String, String> = result.await()

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val params = parseQuery(exchange.requestURI.rawQuery)
            if (exchange.requestURI.path != "/" || (params["code"] == null && params["error"] == null)) {
                exchange.sendResponseHeaders(404, -1)
                return
            }
            val ok = params["code"] != null
            val html = page(
                if (ok) "Signed in" else "Sign-in failed",
                if (ok) "You can close this tab and return to Dyrox Launcher." else "Return to Dyrox Launcher to try again.",
            ).toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, html.size.toLong())
            exchange.responseBody.write(html)
            result.complete(params)
        }
    }

    override fun close() {
        server.stop(0)
    }

    private companion object {
        fun parseQuery(query: String?): Map<String, String> =
            query.orEmpty().split('&').filter { '=' in it }.associate {
                URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
            }

        fun page(title: String, text: String) = """
            <!doctype html><html><head><meta charset="utf-8"><title>Dyrox Launcher</title>
            <style>body{margin:0;height:100vh;display:flex;align-items:center;justify-content:center;
            background:#0d0f12;color:#e8eaed;font-family:system-ui,sans-serif}
            div{padding:32px 40px;border:1px solid #2a2f38;border-radius:14px;background:#15181d;text-align:center}
            h1{color:#a3e635;margin:0 0 8px;font-size:22px}p{margin:0;color:#9aa0a6}</style></head>
            <body><div><h1>$title</h1><p>$text</p></div></body></html>
        """.trimIndent()
    }
}
