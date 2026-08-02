package app.splitup.server.api

import app.splitup.server.auth.JwtSessions
import app.splitup.server.auth.Passwords
import app.splitup.server.db.AccountTable
import app.splitup.server.db.Database
import app.splitup.server.db.ExchangeRateTable
import app.splitup.server.db.SessionTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

const val AUTH_RATE_LIMIT = "auth"
private const val UNIQUE_VIOLATION = "23505"
private const val FX_SCALE = 100_000_000L

fun Application.installRoutes(db: Database, sessions: JwtSessions) {
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        rateLimit(RateLimitName(AUTH_RATE_LIMIT)) {
            post("/auth/register") {
                val body = call.receive<RegisterRequest>()
                require(body.email.contains("@")) { "Invalid email" }
                require(body.password.length >= 8) { "Password must be at least 8 characters" }
                require(body.displayName.isNotBlank()) { "Display name required" }

                val accountId = UUID.randomUUID().toString()
                val hash = Passwords.hash(body.password.toCharArray())
                val now = Clock.System.now()
                try {
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
                } catch (e: ExposedSQLException) {
                    if (e.sqlState == UNIQUE_VIOLATION) {
                        call.respond(HttpStatusCode.Conflict, mapOf("error" to "email_taken"))
                        return@post
                    }
                    throw e
                }
                call.respond(AuthResponse(newSession(db, sessions, accountId), accountId, body.displayName))
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
                call.respond(AuthResponse(newSession(db, sessions, accountId), accountId, row[AccountTable.displayName]))
            }
        }

        post("/auth/logout") {
            val session = call.requireSession(db, sessions) ?: return@post
            transaction(db.exposed) {
                SessionTable.deleteWhere { id eq session.sessionId }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/sync/token") {
            val session = call.requireSession(db, sessions) ?: return@get
            call.respond(
                mapOf(
                    "token" to sessions.issue(
                        accountId = session.accountId,
                        sessionId = session.sessionId,
                        audience = JwtSessions.SYNC_AUDIENCE,
                        ttl = JwtSessions.SYNC_TOKEN_TTL,
                    ),
                ),
            )
        }

        post("/account/identity") {
            val session = call.requireSession(db, sessions) ?: return@post
            val body = call.receive<PersonRow>()
            val personId = transaction(db.exposed) { claimIdentity(session.accountId, body) }
            if (personId == null) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "person_claimed"))
            } else {
                call.respond(IdentityResponse(personId))
            }
        }

        post("/groups/{groupId}/invite") {
            val session = call.requireSession(db, sessions) ?: return@post
            val groupId = call.parameters["groupId"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, mapOf("error" to "missing group"),
            )
            val body = call.receive<InviteRequest>()
            require(body.email.contains("@")) { "Invalid email" }
            val outcome = transaction(db.exposed) {
                inviteToGroup(
                    accountId = session.accountId,
                    groupId = groupId,
                    email = body.email,
                    fallbackPersonId = UUID.randomUUID().toString(),
                    now = Clock.System.now(),
                )
            }
            if (outcome == null) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not_a_member"))
            } else {
                call.respond(InviteResponse(outcome.personId, outcome.claimToken))
            }
        }

        post("/account/claim") {
            val session = call.requireSession(db, sessions) ?: return@post
            val body = call.receive<ClaimRequest>()
            val personId = transaction(db.exposed) {
                claimInvite(session.accountId, body.token.trim(), Clock.System.now())
            }
            if (personId == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "invalid_invite"))
            } else {
                call.respond(IdentityResponse(personId))
            }
        }

        post("/sync/write") {
            val session = call.requireSession(db, sessions) ?: return@post
            val body = call.receive<SyncWriteRequest>()
            val rejected = transaction(db.exposed) { applyBatch(session.accountId, body.ops) }
            rejected.forEach {
                call.application.log.warn("sync/write rejected ${it.table}/${it.id}: ${it.reason}")
            }
            // Deliberately 200 even with rejections: a 4xx wedges the client's
            // upload queue permanently.
            call.respond(SyncWriteResponse(body.ops.size - rejected.size, rejected))
        }

        post("/receipts/scan") {
            call.requireSession(db, sessions) ?: return@post
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
            call.requireSession(db, sessions) ?: return@get
            val from = call.request.queryParameters["from"]?.uppercase()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing 'from'"))
            val to = call.request.queryParameters["to"]?.uppercase()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing 'to'"))
            val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
            val rate = resolveRate(db, from, to, today)
            if (rate == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "no_rate_available"))
            } else {
                call.respond(rate)
            }
        }
    }
}

private fun newSession(db: Database, sessions: JwtSessions, accountId: String): String {
    val sessionId = UUID.randomUUID().toString()
    val now = Clock.System.now()
    transaction(db.exposed) {
        SessionTable.deleteWhere { expiresAt less now }
        SessionTable.insert {
            it[id] = sessionId
            it[SessionTable.accountId] = accountId
            it[createdAt] = now
            it[expiresAt] = now + JwtSessions.SESSION_TTL
        }
    }
    return sessions.issue(accountId, sessionId, JwtSessions.API_AUDIENCE, JwtSessions.SESSION_TTL)
}

/** Returns the live session on success; responds 401 and returns null otherwise. */
private suspend fun ApplicationCall.requireSession(db: Database, sessions: JwtSessions): JwtSessions.Session? {
    val token = request.headers["Authorization"]?.removePrefix("Bearer ")?.trim().orEmpty()
    val parsed = token.takeIf { it.isNotBlank() }?.let { sessions.parse(it, JwtSessions.API_AUDIENCE) }
    val live = parsed != null && transaction(db.exposed) {
        SessionTable.selectAll().where { SessionTable.id eq parsed.sessionId }.any()
    }
    if (!live) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid_or_missing_token"))
        return null
    }
    return parsed
}

/**
 * Rates are stored USD-based (the OpenExchangeRates free plan only serves base
 * USD); arbitrary pairs are derived as usd→to / usd→from in fixed point.
 */
private fun resolveRate(db: Database, from: String, to: String, today: LocalDate): FxRateResponse? {
    if (from == to) return FxRateResponse(from, to, FX_SCALE, today.toString(), "identity")

    fun nearest(fromCode: String, toCode: String) = transaction(db.exposed) {
        ExchangeRateTable.selectAll()
            .where {
                (ExchangeRateTable.fromCode eq fromCode) and
                    (ExchangeRateTable.toCode eq toCode) and
                    (ExchangeRateTable.date lessEq today)
            }
            .orderBy(ExchangeRateTable.date to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
    }

    nearest(from, to)?.let { row ->
        return FxRateResponse(
            from = from,
            to = to,
            rate8 = row[ExchangeRateTable.rate8],
            date = row[ExchangeRateTable.date].toString(),
            source = row[ExchangeRateTable.sourceName],
        )
    }

    val usdTo = if (to == "USD") FX_SCALE to null else nearest("USD", to)?.let { it[ExchangeRateTable.rate8] to it } ?: return null
    val usdFrom = if (from == "USD") FX_SCALE to null else nearest("USD", from)?.let { it[ExchangeRateTable.rate8] to it } ?: return null

    val rate8 = BigDecimal(usdTo.first)
        .multiply(BigDecimal(FX_SCALE))
        .divide(BigDecimal(usdFrom.first), 0, RoundingMode.HALF_UP)
        .toLong()
    if (rate8 <= 0) return null

    val rows = listOfNotNull(usdTo.second, usdFrom.second)
    return FxRateResponse(
        from = from,
        to = to,
        rate8 = rate8,
        date = rows.minOf { it[ExchangeRateTable.date] }.toString(),
        source = rows.first()[ExchangeRateTable.sourceName],
    )
}

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val accountId: String, val displayName: String)

@Serializable
data class IdentityResponse(val personId: String)

@Serializable
data class InviteRequest(val email: String)

@Serializable
data class InviteResponse(val personId: String, val claimToken: String? = null)

@Serializable
data class ClaimRequest(val token: String)

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
