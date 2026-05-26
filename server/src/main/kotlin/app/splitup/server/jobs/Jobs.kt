package app.splitup.server.jobs

import app.splitup.server.db.Database
import io.ktor.server.application.Application

/**
 * Hook for background jobs (FX-rate refresh, settle-up reminders, recurring
 * expenses). Currently a no-op; concrete jobs ship in later versions.
 */
fun Application.startScheduledJobs(@Suppress("UNUSED_PARAMETER") db: Database) {
    environment.log.info("No scheduled jobs registered.")
}
