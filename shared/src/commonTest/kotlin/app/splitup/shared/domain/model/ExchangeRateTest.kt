package app.splitup.shared.domain.model

import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test

class ExchangeRateTest {

    private val date = LocalDate(2026, 1, 1)

    private class TestConverter(private val rates: Map<Pair<String, String>, Long>) : CurrencyConverter {
        private val fetchedAt = Instant.fromEpochSeconds(0)
        override fun rate(from: Currency, to: Currency, date: LocalDate): ExchangeRate? {
            val r = rates[from.code to to.code] ?: return null
            return ExchangeRate(from.code, to.code, r, date, "test", fetchedAt)
        }
    }

    @Test fun `convert USD to INR at 83 rate`() {
        // 1 USD = 83.45 INR
        val conv = TestConverter(mapOf("USD" to "INR" to 8_345_000_000L))
        val tenUsd = Money.parse("10.00", Currency.USD)
        val asInr = conv.convert(tenUsd, Currency.INR, date)!!
        asInr.currency shouldBe Currency.INR
        asInr.format() shouldBe "₹834.50"
    }

    @Test fun `same currency conversion is identity`() {
        val conv = TestConverter(emptyMap())
        val amount = Money.parse("99.99", Currency.INR)
        conv.convert(amount, Currency.INR, date) shouldBe amount
    }

    @Test fun `missing rate returns null`() {
        val conv = TestConverter(emptyMap())
        conv.convert(Money.parse("1.00", Currency.USD), Currency.INR, date) shouldBe null
    }

    @Test fun `convert across different decimal counts JPY to INR`() {
        // JPY has 0 decimals, INR has 2. 1500 JPY at 0.55 INR/JPY = 825 INR.
        // rate8 = 0.55 * 10^8 = 55_000_000
        val conv = TestConverter(mapOf("JPY" to "INR" to 55_000_000L))
        val jpy = Money.parse("1500", Currency.JPY)
        val asInr = conv.convert(jpy, Currency.INR, date)!!
        asInr.format() shouldBe "₹825.00"
    }
}
