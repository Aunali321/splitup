package app.splitup.shared.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Point-in-time exchange rate stored as fixed-point with 8 decimals
 * (rate × 10^8 in a Long) — enough precision for any real-world conversion
 * without floating-point drift.
 */
@Serializable
data class ExchangeRate(
    val from: String,
    val to: String,
    val rate8: Long,
    val date: LocalDate,
    val source: String,
    val fetchedAt: Instant,
) {
    init {
        require(from.length == 3 && to.length == 3) { "Currency codes must be ISO 4217" }
        require(from != to) { "Self-rate is always 1.0" }
        require(rate8 > 0) { "Rate must be positive" }
    }
    companion object { const val SCALE: Long = 100_000_000L }
}

interface CurrencyConverter {
    fun rate(from: Currency, to: Currency, date: LocalDate): ExchangeRate?

    fun convert(amount: Money, to: Currency, date: LocalDate): Money? {
        if (amount.currency == to) return amount
        val rate = rate(amount.currency, to, date) ?: return null
        val numerator = amount.minorUnits * rate.rate8 * to.scale
        val denominator = ExchangeRate.SCALE * amount.currency.scale
        return Money.ofMinor(numerator / denominator, to)
    }
}

object NoOpCurrencyConverter : CurrencyConverter {
    override fun rate(from: Currency, to: Currency, date: LocalDate): ExchangeRate? = null
}
