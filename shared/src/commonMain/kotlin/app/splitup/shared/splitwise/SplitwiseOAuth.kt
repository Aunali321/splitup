package app.splitup.shared.splitwise

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.Digest
import io.ktor.util.encodeBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SplitwiseOAuth(
    private val engineProvider: () -> HttpClientEngine,
    private val credentials: SplitwiseCredentials = SharedSplitwiseCredentials.PROD,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** PKCE (RFC 7636, S256) pair for one authorization attempt. */
    data class Pkce(val verifier: String, val challenge: String)

    @OptIn(ExperimentalUuidApi::class)
    suspend fun createPkce(): Pkce {
        val verifier = Uuid.random().toString() + Uuid.random().toString()
        val digest = Digest("SHA-256")
        digest += verifier.encodeToByteArray()
        val challenge = digest.build()
            .encodeBase64()
            .replace('+', '-')
            .replace('/', '_')
            .trimEnd('=')
        return Pkce(verifier, challenge)
    }

    fun buildAuthorizeUrl(state: String, codeChallenge: String): String {
        val params = listOf(
            "client_id" to credentials.consumerKey,
            "redirect_uri" to credentials.redirectUri,
            "response_type" to "code",
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
        )
        val query = params.joinToString("&") { (k, v) -> "$k=" + urlEncode(v) }
        return "https://secure.splitwise.com/oauth/authorize?$query"
    }

    suspend fun exchangeCode(code: String, codeVerifier: String): TokenResponse {
        val client = HttpClient(engineProvider()) {
            install(ContentNegotiation) { json(json) }
        }
        try {
            return client.submitForm(
                url = "https://secure.splitwise.com/oauth/token",
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", credentials.redirectUri)
                    append("client_id", credentials.consumerKey)
                    append("client_secret", credentials.consumerSecret)
                    append("code_verifier", codeVerifier)
                },
            ).body()
        } finally {
            client.close()
        }
    }

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String = "Bearer",
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("scope") val scope: String? = null,
    )

    private fun urlEncode(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when {
                c.isLetterOrDigit() || c in "-_.~" -> sb.append(c)
                else -> for (b in c.toString().encodeToByteArray()) {
                    sb.append('%')
                    sb.append(((b.toInt() ushr 4) and 0xF).toString(16).uppercase())
                    sb.append((b.toInt() and 0xF).toString(16).uppercase())
                }
            }
        }
        return sb.toString()
    }
}

@Serializable
data class SplitwiseCredentials(
    val consumerKey: String,
    val consumerSecret: String,
    val redirectUri: String,
)

/**
 * Embedded "Migration" OAuth app credentials. Every install uses the same client
 * so end users don't have to register their own Splitwise app to migrate. The
 * consumerSecret is therefore public by design and provides no client
 * authentication — the flow's real protections are the per-attempt `state`
 * check and PKCE. Forks replace these and add their own redirectUri on the
 * Splitwise app config side.
 */
object SharedSplitwiseCredentials {
    val PROD = SplitwiseCredentials(
        consumerKey = "AbsAZrqFBsb52kYLlA5rzFm66MGAt9YN4ET05sTL",
        consumerSecret = "zqvledHMpB5Fr4iWQVShLs1oYKOttuSkPlZOJRvp",
        redirectUri = "splitup://oauth/splitwise",
    )
}
