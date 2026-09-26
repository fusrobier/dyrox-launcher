package net.dyrox.shared.auth

import kotlinx.coroutines.runBlocking
import net.dyrox.shared.http.HttpService
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MicrosoftAuthenticatorTest {
    private val server = FakeAuthServer()
    private val authenticator = MicrosoftAuthenticator(
        http = HttpService("dyrox-test"),
        clientId = "test-client-id",
        endpoints = server.endpoints,
        clock = { 1_000_000L },
        sleep = {}, // no real waiting between device-code polls
    )

    @AfterTest
    fun stop() = server.close()

    private fun form(body: String): Map<String, String> = body.split('&').associate {
        URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
    }

    @Test
    fun `device code flow walks the whole chain`(): Unit = runBlocking {
        server.happyPath()
        server.on(
            "POST", "/ms/token",
            400 to """{"error":"authorization_pending","error_description":"waiting"}""",
            400 to """{"error":"slow_down"}""",
            200 to """{"access_token":"ms-access","refresh_token":"refresh-1","expires_in":3600}""",
        )
        var prompt: DeviceCodePrompt? = null
        val result = authenticator.loginWithDeviceCode { prompt = it }

        assertEquals("ABCD-1234", prompt?.userCode)
        assertEquals("https://www.microsoft.com/link", prompt?.verificationUri)
        assertEquals(1_000_000L + 900_000L, prompt?.expiresAt)

        assertEquals("Notch", result.profile.name)
        assertEquals("069a79f444e94726a5befca90e38aaf5", result.profile.id)
        assertEquals("http://textures.minecraft.net/texture/abc", result.profile.activeSkinUrl)
        assertEquals("refresh-1", result.refreshToken)
        assertEquals(1_000_000L + 86_400_000L, result.minecraftTokenExpiresAt)
        assertEquals("2535400000000001", result.xuid)
        assertEquals(3, server.requestsTo("/ms/token").size, "should poll until success")
    }

    @Test
    fun `requests have the shapes Microsoft, Xbox and Mojang expect`(): Unit = runBlocking {
        server.happyPath()
        authenticator.loginWithDeviceCode {}

        val deviceCode = form(server.requestsTo("/ms/devicecode").single().body)
        assertEquals("test-client-id", deviceCode["client_id"])
        assertEquals("XboxLive.signin offline_access", deviceCode["scope"])

        val poll = form(server.requestsTo("/ms/token").single().body)
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", poll["grant_type"])
        assertEquals("dev-code", poll["device_code"])

        val xbl = server.requestsTo("/xbl").single()
        assertTrue(""""RpsTicket":"d=ms-access"""" in xbl.body)
        assertTrue(""""RelyingParty":"http://auth.xboxlive.com"""" in xbl.body)
        assertEquals("application/json", xbl.headers["content-type"])

        val xsts = server.requestsTo("/xsts").single()
        assertTrue(""""UserTokens":["xbl-token"]""" in xsts.body)
        assertTrue(""""RelyingParty":"rp://api.minecraftservices.com/"""" in xsts.body)

        val login = server.requestsTo("/mc/authentication/login_with_xbox").single()
        assertTrue(""""identityToken":"XBL3.0 x=user-hash;xsts-token"""" in login.body)

        val profile = server.requestsTo("/mc/minecraft/profile").single()
        assertTrue(profile.headers["authorization"]!!.startsWith("Bearer "))
    }

    @Test
    fun `declined device code sign-in is reported`(): Unit = runBlocking {
        server.happyPath()
        server.on("POST", "/ms/token", 400 to """{"error":"authorization_declined"}""")
        val error = assertFailsWith<MicrosoftAuthException> { authenticator.loginWithDeviceCode {} }
        assertEquals("authorization_declined", error.error)
        assertEquals(0, server.requestsTo("/xbl").size)
    }

    @Test
    fun `XSTS XErr codes become readable errors`(): Unit = runBlocking {
        server.happyPath()
        server.on("POST", "/xsts", 401 to """{"Identity":"0","XErr":2148916233,"Message":"","Redirect":"https://start.ui.xboxlive.com/CreateAccount"}""")
        val error = assertFailsWith<XboxAuthException> { authenticator.loginWithDeviceCode {} }
        assertEquals(2148916233L, error.xErr)
        assertTrue("no Xbox profile" in error.message!!)

        assertTrue("child account" in XboxAuthException.describe(2148916238))
    }

    @Test
    fun `unapproved Azure app is detected`(): Unit = runBlocking {
        server.happyPath()
        server.on(
            "POST", "/mc/authentication/login_with_xbox",
            403 to """{"path":"/authentication/login_with_xbox","errorMessage":"Invalid app registration, see https://aka.ms/AppRegInfo for more information"}""",
        )
        assertFailsWith<AppNotApprovedException> { authenticator.loginWithDeviceCode {} }
    }

    @Test
    fun `account without Minecraft profile is detected`(): Unit = runBlocking {
        server.happyPath()
        server.on("GET", "/mc/minecraft/profile", 404 to """{"path":"/minecraft/profile","errorType":"NOT_FOUND","error":"NOT_FOUND"}""")
        assertFailsWith<NoMinecraftProfileException> { authenticator.loginWithDeviceCode {} }
    }

    @Test
    fun `refresh uses the refresh token and keeps it if Microsoft sends none`(): Unit = runBlocking {
        server.happyPath()
        server.on("POST", "/ms/token", 200 to """{"access_token":"ms-access-2","expires_in":3600}""")
        val result = authenticator.refresh("old-refresh")
        assertEquals("old-refresh", result.refreshToken)
        val body = form(server.requestsTo("/ms/token").single().body)
        assertEquals("refresh_token", body["grant_type"])
        assertEquals("old-refresh", body["refresh_token"])
    }

    @Test
    fun `revoked refresh token means sign in again`(): Unit = runBlocking {
        server.on("POST", "/ms/token", 400 to """{"error":"invalid_grant","error_description":"AADSTS70000: expired"}""")
        assertFailsWith<InvalidRefreshTokenException> { authenticator.refresh("dead") }
    }

    @Test
    fun `rejected Minecraft token is reported by fetchProfile`(): Unit = runBlocking {
        server.on("GET", "/mc/minecraft/profile", 401 to "")
        assertFailsWith<MinecraftTokenRejectedException> { authenticator.fetchProfile("stale") }
    }

    @Test
    fun `browser flow uses PKCE, checks state and exchanges the code`(): Unit = runBlocking {
        server.happyPath()
        var authorizeUrl = ""
        val result = authenticator.loginWithBrowser(openBrowser = { url ->
            authorizeUrl = url
            val query = URI(url).rawQuery.split('&').associate {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
            }
            // Play the browser: follow the redirect back to the loopback receiver.
            thread {
                val redirect = "${query["redirect_uri"]}/?code=auth-code&state=${query["state"]}"
                HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI(redirect)).build(), HttpResponse.BodyHandlers.discarding())
            }
        })
        assertEquals("Notch", result.profile.name)
        assertTrue("code_challenge_method=S256" in authorizeUrl)
        assertTrue("redirect_uri=http%3A%2F%2Flocalhost%3A" in authorizeUrl)

        val exchange = form(server.requestsTo("/ms/token").single().body)
        assertEquals("authorization_code", exchange["grant_type"])
        assertEquals("auth-code", exchange["code"])
        val challenge = URLDecoder.decode(authorizeUrl.substringAfter("code_challenge=").substringBefore('&'), Charsets.UTF_8)
        assertEquals(challenge, Pkce.fromVerifier(exchange.getValue("code_verifier")).challenge)
    }

    @Test
    fun `browser flow rejects a mismatched state`(): Unit = runBlocking {
        server.happyPath()
        assertFailsWith<AuthException> {
            authenticator.loginWithBrowser(openBrowser = { url ->
                val redirectUri = URLDecoder.decode(url.substringAfter("redirect_uri=").substringBefore('&'), Charsets.UTF_8)
                thread {
                    HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI("$redirectUri/?code=x&state=forged")).build(),
                        HttpResponse.BodyHandlers.discarding(),
                    )
                }
            })
        }
        assertEquals(0, server.requestsTo("/ms/token").size, "code must not be exchanged")
    }

    @Test
    fun `PKCE matches the RFC 7636 test vector`() {
        val pkce = Pkce.fromVerifier("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkce.challenge)
        assertTrue(Pkce.generate().verifier.length >= 43)
    }

    @Test
    fun `reads claims from JWT payloads`() {
        assertEquals("2535400000000009", Jwt.stringClaim(FakeAuthServer.minecraftToken("2535400000000009"), "xuid"))
        assertEquals(null, Jwt.stringClaim("not-a-jwt", "xuid"))
    }
}
