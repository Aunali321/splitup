package app.splitup.server.api

import app.splitup.server.auth.JwtSessions
import app.splitup.server.auth.Passwords
import app.splitup.server.db.AccountTable
import app.splitup.server.db.Database
import app.splitup.server.db.ExchangeRateTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.util.UUID

private val SYNC_TOKEN_TTL: Duration = Duration.ofHours(1)

fun Application.installRoutes(db: Database, sessions: JwtSessions) {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        post("/auth/register") {
            val body = call.receive<RegisterRequest>()
            require(body.email.contains("@")) { "Invalid email" }
            require(body.password.length >= 8) { "Password must be at least 8 characters" }
            require(body.displayName.isNotBlank()) { "Display name required" }

            val existing = transaction(db.exposed) {
                AccountTable.selectAll()
                    .where { AccountTable.email eq body.email.lowercase() }
                    .firstOrNull()
            }
            if (existing != null) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "email_taken"))
                return@post
            }

            val accountId = UUID.randomUUID().toString()
            val hash = Passwords.hash(body.password.toCharArray())
            val now = Clock.System.now()
            transaction(db.exposed) {
                AccountTable.insert {
                    it[id] = accountId
                    it[email] = body.email.lowercase()
                    it[passwordHash] = hash
                    it[displayName] = body.displayName
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            call.respond(AuthResponse(sessions.issue(accountId), accountId, body.displayName))
        }

        post("/auth/login") {
            val body = call.receive<LoginRequest>()
            val row = transaction(db.exposed) {
                AccountTable.selectAll()
                    .where { AccountTable.email eq body.email.lowercase() }
                    .firstOrNull()
            }
            if (row == null || !Passwords.verify(row[AccountTable.passwordHash], body.password.toCharArray())) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid_credentials"))
                return@post
            }
            val accountId = row[AccountTable.id]
            call.respond(AuthResponse(sessions.issue(accountId), accountId, row[AccountTable.displayName]))
        }

        get("/sync/token") {
            val accountId = call.requireAccount(sessions) ?: return@get
            call.respond(mapOf("token" to sessions.issue(accountId, ttl = SYNC_TOKEN_TTL)))
        }

        post("/receipts/scan") {
            call.requireAccount(sessions) ?: return@post
            val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
            call.respond(
                OcrResult(
                    merchant = "Sample Merchant",
                    total = "0.00",
                    currencyCode = "USD",
                    date = today.toString(),
                    lineItems = emptyList(),
                    note = "OCR provider not configured.",
                ),
            )
        }

        get("/fx/latest") {
            call.requireAccount(sessions) ?: return@get
            val from = call.request.queryParameters["from"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing 'from'"))
            val to = call.request.queryParameters["to"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing 'to'"))
            val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
            val row = transaction(db.exposed) {
                ExchangeRateTable.selectAll()
                    .where {
                        (ExchangeRateTable.fromCode eq from) and
                            (ExchangeRateTable.toCode eq to) and
                            (ExchangeRateTable.date lessEq today)
                    }
                    .orderBy(ExchangeRateTable.date to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
            }
            if (row == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "no_rate_available"))
            } else {
                call.respond(
                    FxRateResponse(
                        from = row[ExchangeRateTable.fromCode],
                        to = row[ExchangeRateTable.toCode],
                        rate8 = row[ExchangeRateTable.rate8],
                        date = row[ExchangeRateTable.date].toString(),
                        source = row[ExchangeRateTable.sourceName],
                    ),
                )
            }
        }
    }
}

/** Returns the account id on success; responds 401 and returns null otherwise. */
private suspend fun ApplicationCall.requireAccount(sessions: JwtSessions): String? {
    val token = request.headers["Authorization"]?.removePrefix("Bearer ")?.trim().orEmpty()
    val sub = if (token.isNotBlank()) sessions.parse(token) else null
    if (sub == null) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid_or_missing_token"))
        return null
    }
    return sub
}

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val accountId: String, val displayName: String)

@Serializable
data class OcrLineItem(val description: String, val amount: String)

@Serializable
data class OcrResult(
    val merchant: String,
    val total: String,
    val currencyCode: String,
    val date: String,
    val lineItems: List<OcrLineItem>,
    val note: String? = null,
)

@Serializable
data class FxRateResponse(
    val from: String,
    val to: String,
    val rate8: Long,
    val date: String,
    val source: String,
)
