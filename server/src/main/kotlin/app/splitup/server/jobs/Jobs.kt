package app.splitup.server.jobs

import app.splitup.server.ServerConfig
import app.splitup.server.db.Database
import app.splitup.server.db.ExchangeRateTable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.math.BigDecimal
import kotlin.time.Duration.Companion.hours

private val REFRESH_INTERVAL = 12.hours
private const val SOURCE = "openexchangerates"

/**
 * Refreshes exchange rates from OpenExchangeRates twice a day. The free plan
 * only serves USD-based rates; /fx/latest derives cross pairs from these rows.
 */
fun Application.startScheduledJobs(db: Database, cfg: ServerConfig) {
    val apiKey = cfg.openExchangeRatesApiKey
    if (apiKey == null) {
        log.info("OPEN_EXCHANGE_RATES_API_KEY not set — /fx/latest has no rate source.")
        return
    }
    launch {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            while (true) {
                runCatching { refreshRates(client, apiKey, db) }
                    .onSuccess { log.info("FX rates refreshed ($it currencies)") }
                    .onFailure { log.warn("FX refresh failed: ${it.message}") }
                delay(REFRESH_INTERVAL)
            }
        } finally {
            client.close()
        }
    }
}

private suspend fun refreshRates(client: HttpClient, apiKey: String, db: Database): Int {
    val response: OerLatestResponse = client.get("https://openexchangerates.org/api/latest.json") {
        parameter("app_id", apiKey)
    }.body()
    val date = Instant.fromEpochSeconds(response.timestamp).toLocalDateTime(TimeZone.UTC).date
    val now = Clock.System.now()
    val rows = response.rates.filterKeys { it.length == 3 && it != response.base }
    transaction(db.exposed) {
        rows.forEach { (code, rate) ->
            val rate8 = BigDecimal.valueOf(rate).movePointRight(8).toLong()
            if (rate8 <= 0) return@forEach
            ExchangeRateTable.upsert {
                it[fromCode] = response.base
                it[toCode] = code
                it[ExchangeRateTable.rate8] = rate8
                it[ExchangeRateTable.date] = date
                it[sourceName] = SOURCE
                it[fetchedAt] = now
            }
        }
    }
    return rows.size
}

@Serializable
private data class OerLatestResponse(
    val base: String,
    val timestamp: Long,
    val rates: Map<String, Double>,
)
