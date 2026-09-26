package net.dyrox.shared.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Proof Key for Code Exchange (RFC 7636, S256), protecting the browser flow's authorization code. */
data class Pkce(val verifier: String, val challenge: String) {
    companion object {
        private val random = SecureRandom()
        private val base64Url = Base64.getUrlEncoder().withoutPadding()

        fun generate(): Pkce = fromVerifier(randomUrlSafe(32))

        fun fromVerifier(verifier: String): Pkce {
            val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Pkce(verifier, base64Url.encodeToString(hash))
        }

        /** [bytes] random bytes, base64url-encoded; also used for the OAuth `state` parameter. */
        fun randomUrlSafe(bytes: Int): String = base64Url.encodeToString(ByteArray(bytes).also(random::nextBytes))
    }
}
