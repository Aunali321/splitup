package app.splitup.server.auth

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.Base64
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Mints and verifies HS256 JWTs. Two audiences share the signing key but are
 * never interchangeable: [API_AUDIENCE] tokens authenticate API calls,
 * [SYNC_AUDIENCE] tokens are consumed only by PowerSync (its static JWK is the
 * same secret — the JWK `k` field must be base64url per RFC 7518 §6.4.1, so we
 * decode the secret here in the same form).
 *
 * Every token carries the id of a server-side session row as `jti`; deleting
 * that row (logout) invalidates all tokens minted for it.
 */
class JwtSessions(secretBase64Url: String) {

    data class Session(val accountId: String, val sessionId: String)

    private val key: ByteArray = decodeBase64Url(secretBase64Url).also {
        require(it.size >= 32) {
            "SPLITUP_SESSION_SECRET must decode to at least 32 bytes — generate with " +
                "'openssl rand -base64 32 | tr +/ -_ | tr -d ='"
        }
    }
    private val signer = MACSigner(key)
    private val verifier = MACVerifier(key)

    fun issue(accountId: String, sessionId: String, audience: String, ttl: Duration): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .subject(accountId)
            .jwtID(sessionId)
            .issuer(ISSUER)
            .audience(audience)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(ttl.inWholeSeconds)))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.HS256)
            .type(JOSEObjectType.JWT)
            .keyID(KEY_ID)
            .build()
        return SignedJWT(header, claims).apply { sign(signer) }.serialize()
    }

    fun parse(token: String, audience: String): Session? = runCatching {
        val jwt = SignedJWT.parse(token)
        if (!jwt.verify(verifier)) return null
        val claims = jwt.jwtClaimsSet
        if (claims.issuer != ISSUER) return null
        if (audience !in claims.audience.orEmpty()) return null
        val exp = claims.expirationTime?.toInstant() ?: return null
        if (Instant.now().isAfter(exp)) return null
        val accountId = claims.subject ?: return null
        val sessionId = claims.getJWTID() ?: return null
        Session(accountId, sessionId)
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
        const val API_AUDIENCE = "splitup"
        const val SYNC_AUDIENCE = "powersync"
        const val KEY_ID = "splitup-session"
        val SESSION_TTL: Duration = 60.days
        val SYNC_TOKEN_TTL: Duration = 1.hours
    }
}
