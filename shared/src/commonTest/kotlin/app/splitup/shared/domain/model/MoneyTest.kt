package app.splitup.shared.domain.model

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test fun `parse INR round trip`() {
        val m = Money.parse("1234.56", Currency.INR)
        m.minorUnits shouldBe 123_456L
        m.format() shouldBe "₹1234.56"
    }

    @Test fun `parse USD round trip`() {
        val m = Money.parse("12.34", Currency.USD)
        m.minorUnits shouldBe 1234L
        m.format() shouldBe "$12.34"
    }

    @Test fun `parse negative`() {
        Money.parse("-0.05", Currency.USD).minorUnits shouldBe -5L
    }

    @Test fun `JPY has no decimals`() {
        Money.parse("1500", Currency.JPY).format() shouldBe "¥1500"
    }

    @Test fun `parse rejects excess decimals`() {
        assertFailsWith<IllegalArgumentException> { Money.parse("1.234", Currency.USD) }
    }

    @Test fun `arithmetic in same currency`() {
        val a = Money.parse("10.00", Currency.USD)
        val b = Money.parse("3.50", Currency.USD)
        (a + b).format() shouldBe "$13.50"
        (a - b).format() shouldBe "$6.50"
        (b * 3).format() shouldBe "$10.50"
        (-a).format() shouldBe "-$10.00"
    }

    @Test fun `arithmetic across currencies is rejected`() {
        val usd = Money.parse("10.00", Currency.USD)
        val inr = Money.parse("10.00", Currency.INR)
        assertFailsWith<IllegalArgumentException> { usd + inr }
        assertFailsWith<IllegalArgumentException> { usd - inr }
        assertFailsWith<IllegalArgumentException> { usd.compareTo(inr) }
    }

    @Test fun `format pads minor units`() {
        Money.ofMinor(5, Currency.USD).format() shouldBe "$0.05"
        Money.ofMinor(50, Currency.USD).format() shouldBe "$0.50"
        Money.ofMinor(500, Currency.USD).format() shouldBe "$5.00"
    }

    @Test fun `default fallback currency is USD`() {
        // The user picks their real home currency in onboarding; DEFAULT is only
        // a fallback for paths that run before onboarding completes.
        Currency.DEFAULT shouldBe Currency.USD
    }

    @Test fun `lookup by code is case-insensitive`() {
        Currency.ofCode("inr") shouldBe Currency.INR
        Currency.ofCode("USD") shouldBe Currency.USD
        Currency.ofCode("ZZZ") shouldBe null
    }
}
