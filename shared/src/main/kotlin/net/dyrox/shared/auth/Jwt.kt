package net.dyrox.shared.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.dyrox.shared.json.DyroxJson
import java.util.Base64

/**
 * Reads claims from a JWT payload WITHOUT verifying the signature. Only for informational values
 * from tokens we just received over TLS (e.g. the `xuid` in a Minecraft access token); never for trust decisions.
 */
object Jwt {
    fun claims(token: String): JsonObject? {
        val payload = token.split('.').getOrNull(1) ?: return null
        return runCatching {
            val json = Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '='))
            DyroxJson.parseToJsonElement(json.toString(Charsets.UTF_8)) as? JsonObject
        }.getOrNull()
    }

    fun stringClaim(token: String, name: String): String? =
        (claims(token)?.get(name) as? JsonPrimitive)?.content
}
