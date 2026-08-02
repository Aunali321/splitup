package app.splitup.server

import app.splitup.server.api.AUTH_RATE_LIMIT
import app.splitup.server.api.installRoutes
import app.splitup.server.auth.JwtSessions
import app.splitup.server.db.Database
import app.splitup.server.jobs.startScheduledJobs
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes

fun main() {
    val cfg = ServerConfig.fromEnv()
    val db = Database.connect(cfg.databaseUrl, cfg.databaseUser, cfg.databasePassword)
    db.migrate()
    val sessions = JwtSessions(cfg.sessionSecret)

    embeddedServer(Netty, port = cfg.port, host = "0.0.0.0") {
        configure(cfg)
        installRoutes(db, sessions)
        startScheduledJobs(db, cfg)
    }.start(wait = true)
}

private fun Application.configure(cfg: ServerConfig) {
    if (cfg.behindProxy) install(XForwardedHeaders)
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
    // Bearer-token auth, no cookies — credentials are deliberately NOT allowed,
    // which is what makes anyHost safe for a public API.
    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowHeader("Authorization")
        allowNonSimpleContentTypes = true
    }
    install(RateLimit) {
        register(RateLimitName(AUTH_RATE_LIMIT)) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
    install(CallLogging)
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "bad request")))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "internal_server_error"))
        }
    }
}
