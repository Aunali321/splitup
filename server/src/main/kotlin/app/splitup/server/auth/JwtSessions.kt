package app.splitup.server.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * Mints and verifies HS256 session JWTs. The same key is consumed by PowerSync
 * as a static JWK — the JWK `k` field must be base64url (URL-safe, unpadded)
 * per RFC 7518 §6.4.1, so we decode the secret here in the same form.
 */
class JwtSessions(secretBase64Url: String) {

    private val key: ByteArray = decodeBase64Url(secretBase64Url).also {
        require(it.size >= 32) {
            "SPLITUP_SESSION_SECRET must decode to at least 32 bytes — generate with " +
                "'openssl rand -base64 32 | tr +/ -_ | tr -d ='"
        }
    }
    private val signer = MACSigner(key)
    private val verifier = MACVerifier(key)

    fun issue(accountId: String, ttl: Duration = DEFAULT_TTL): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .subject(accountId)
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ttl)))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.HS256)
            .type(JOSEObjectType.JWT)
            .keyID(KEY_ID)
            .build()
        return SignedJWT(header, claims).apply { sign(signer) }.serialize()
    }

    fun parse(token: String): String? = runCatching {
        val jwt = SignedJWT.parse(token)
        if (!jwt.verify(verifier)) return null
        val claims = jwt.jwtClaimsSet
        val exp = claims.expirationTime?.toInstant() ?: return null
        if (Instant.now().isAfter(exp)) return null
        claims.subject
    }.getOrNull()

    private fun decodeBase64Url(raw: String): ByteArray {
        val normalised = raw
            .replace('-', '+')
            .replace('_', '/')
            .let {
                val pad = (4 - it.length % 4) % 4
                it + "=".repeat(pad)
            }
        return Base64.getDecoder().decode(normalised)
    }

    companion object {
        private const val ISSUER = "splitup-server"
        const val AUDIENCE = "splitup"
        const val KEY_ID = "splitup-session"
        private val DEFAULT_TTL: Duration = Duration.ofDays(60)
    }
}
