package app.splitup.server

import app.splitup.server.api.installRoutes
import app.splitup.server.auth.JwtSessions
import app.splitup.server.db.Database
import app.splitup.server.jobs.startScheduledJobs
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

fun main() {
    val cfg = ServerConfig.fromEnv()
    val db = Database.connect(cfg.databaseUrl, cfg.databaseUser, cfg.databasePassword)
    db.migrate()
    val sessions = JwtSessions(cfg.sessionSecret)

    embeddedServer(Netty, port = cfg.port, host = "0.0.0.0") {
        configure()
        installRoutes(db, sessions)
        startScheduledJobs(db)
    }.start(wait = true)
}

private fun Application.configure() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
        allowHeader("Authorization")
        allowCredentials = true
        allowNonSimpleContentTypes = true
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
