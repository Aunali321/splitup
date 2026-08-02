package app.splitup.shared.data.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class SyncApiException(message: String) : Exception(message)

/** Talks to the splitup server. Tokens are supplied per call by [SyncService]. */
class SyncApi(engine: HttpClientEngine, private val baseUrl: () -> String) {
    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        defaultRequest {
            val base = baseUrl()
            url.takeFrom(if (base.endsWith("/")) base else "$base/")
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun register(email: String, password: String, displayName: String): AuthResponse =
        client.post("auth/register") { setBody(RegisterRequest(email, password, displayName)) }.expect()

    suspend fun login(email: String, password: String): AuthResponse =
        client.post("auth/login") { setBody(LoginRequest(email, password)) }.expect()

    suspend fun logout(token: String) {
        client.post("auth/logout") { header("Authorization", "Bearer $token") }
    }

    suspend fun syncToken(token: String): String =
        client.get("sync/token") { header("Authorization", "Bearer $token") }
            .expect<SyncTokenResponse>().token

    suspend fun claimIdentity(token: String, person: JsonObject): String? {
        val response: HttpResponse = client.post("account/identity") {
            header("Authorization", "Bearer $token")
            setBody(person)
        }
        return if (response.status == HttpStatusCode.Conflict) null
        else response.expect<IdentityResponse>().personId
    }

    /** Returns the canonical person id, or null when the token is unknown or spent. */
    suspend fun claimInvite(token: String, inviteCode: String): String? {
        val response: HttpResponse = client.post("account/claim") {
            header("Authorization", "Bearer $token")
            setBody(ClaimRequest(inviteCode))
        }
        return if (response.status == HttpStatusCode.NotFound) null
        else response.expect<IdentityResponse>().personId
    }

    /** Returns the invite outcome, or null when the caller is not a member. */
    suspend fun invite(token: String, groupId: String, email: String): InviteResponse? {
        val response: HttpResponse = client.post("groups/$groupId/invite") {
            header("Authorization", "Bearer $token")
            setBody(InviteRequest(email))
        }
        return if (response.status == HttpStatusCode.Forbidden) null
        else response.expect<InviteResponse>()
    }

    suspend fun write(token: String, ops: List<SyncOp>): SyncWriteResponse =
        client.post("sync/write") {
            header("Authorization", "Bearer $token")
            setBody(SyncWriteRequest(ops))
        }.expect()

    fun close() = client.close()
}

private suspend inline fun <reified T> HttpResponse.expect(): T {
    if (!status.isSuccess()) throw SyncApiException(errorMessage())
    return body()
}

/** The server reports errors as `{"error": "code"}`; anything else gets the status. */
private suspend fun HttpResponse.errorMessage(): String {
    val code = runCatching { body<ErrorBody>().error }.getOrNull()
    return when (code) {
        "email_taken" -> "That email is already registered"
        "invalid_credentials" -> "Wrong email or password"
        "invalid_invite" -> "Invalid or already-used invite code"
        "invalid_or_missing_token" -> "Session expired — sign in again"
        null -> "Server error (${status.value})"
        else -> code.replace('_', ' ')
    }
}

@Serializable
private data class ErrorBody(val error: String)

@Serializable
private data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
private data class LoginRequest(val email: String, val password: String)

@Serializable
private data class SyncTokenResponse(val token: String)

@Serializable
private data class IdentityResponse(val personId: String)

@Serializable
private data class InviteRequest(val email: String)

@Serializable
private data class ClaimRequest(val token: String)

@Serializable
data class InviteResponse(val personId: String, val claimToken: String? = null)

@Serializable
data class AuthResponse(val token: String, val accountId: String, val displayName: String)

@Serializable
data class SyncWriteRequest(val ops: List<SyncOp>)

@Serializable
data class SyncWriteResponse(val applied: Int, val rejected: List<Rejection>)

@Serializable
data class Rejection(val table: SyncTable, val id: String, val reason: String)

@Serializable
data class SyncOp(
    val op: OpKind,
    val table: SyncTable,
    val id: String,
    val row: JsonObject? = null,
)

@Serializable
enum class OpKind { PUT, DELETE }

@Serializable
enum class SyncTable(val tableName: String) {
    PERSON("person"),
    GROUP("group_"),
    GROUP_MEMBER("group_member"),
    EXPENSE("expense"),
    EXPENSE_SHARE("expense_share"),
    COMMENT("comment"),
    ;

    companion object {
        private val byTableName = entries.associateBy { it.tableName }
        fun of(tableName: String): SyncTable? = byTableName[tableName]
    }
}
