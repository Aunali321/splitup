package app.splitup.shared.splitwise

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Read-only client for the public Splitwise API at secure.splitwise.com.
 * Uses Bearer auth with the user-supplied API key.
 *
 * Splitwise has undocumented but conservative rate limits — the retry plugin
 * uses exponential backoff and the importer fetches in pages of 100.
 */
class SplitwiseClient(
    engine: HttpClientEngine,
    private val apiKey: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client = HttpClient(engine) {
        // Without this a 401/429 body is handed to the success deserializer and the
        // user sees "Field 'expenses' is required" instead of what actually happened.
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 4)
            // 429 is the limit this API actually enforces, and it is not a server
            // error, so retryOnServerErrors alone would let a throttle kill the import.
            retryIf(maxRetries = 4) { _, response -> response.status == HttpStatusCode.TooManyRequests }
            exponentialDelay()
        }
        install(Logging) { level = LogLevel.INFO }
        install(Auth) {
            bearer { loadTokens { BearerTokens(apiKey, apiKey) } }
        }
        defaultRequest {
            url.takeFrom("https://secure.splitwise.com/api/v3.0/")
        }
    }

    suspend fun getCurrentUser(): SwUser =
        client.get("get_current_user").body<SwCurrentUserResponse>().user

    suspend fun getGroups(): List<SwGroup> =
        client.get("get_groups").body<SwGroupsResponse>().groups

    suspend fun getFriends(): List<SwFriend> =
        client.get("get_friends").body<SwFriendsResponse>().friends

    suspend fun getCurrencies(): List<SwCurrency> =
        client.get("get_currencies").body<SwCurrenciesResponse>().currencies

    /** Paginated. limit is capped at 100 by Splitwise; iterate until empty. */
    suspend fun getExpensesPage(
        limit: Int = 100,
        offset: Int = 0,
        updatedAfter: String? = null,
    ): List<SwExpense> = client.get("get_expenses") {
        parameter("limit", limit)
        parameter("offset", offset)
        if (updatedAfter != null) parameter("updated_after", updatedAfter)
    }.body<SwExpensesResponse>().expenses

    suspend fun getComments(expenseId: Long): List<SwComment> =
        client.get("get_comments") { parameter("expense_id", expenseId) }
            .body<SwCommentsResponse>().comments

    fun close() = client.close()
}
