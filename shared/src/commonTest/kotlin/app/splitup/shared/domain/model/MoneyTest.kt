package app.splitup.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MoneyTest {

    @Test fun parseInrRoundTrip() {
        val m = Money.parse("1234.56", Currency.INR)
        assertEquals(123_456L, m.minorUnits)
        assertEquals("₹1,234.56", m.format())
    }

    @Test fun parseUsdRoundTrip() {
        val m = Money.parse("12.34", Currency.USD)
        assertEquals(1234L, m.minorUnits)
        assertEquals("$12.34", m.format())
    }

    @Test fun parseNegative() {
        assertEquals(-5L, Money.parse("-0.05", Currency.USD).minorUnits)
    }

    @Test fun jpyHasNoDecimals() {
        assertEquals("¥1,500", Money.parse("1500", Currency.JPY).format())
    }

    @Test fun parseToleratesTrailingZeros() {
        assertEquals(1000L, Money.parse("1000.0", Currency.JPY).minorUnits)
        assertEquals(1250L, Money.parse("12.500", Currency.USD).minorUnits)
    }

    @Test fun parseRejectsExcessDecimals() {
        assertFailsWith<IllegalArgumentException> { Money.parse("1.234", Currency.USD) }
    }

    @Test fun westernGroupingByThousands() {
        assertEquals("$1,234,567.89", Money.ofMinor(123_456_789L, Currency.USD).format())
        assertEquals("$999.99", Money.ofMinor(99_999L, Currency.USD).format())
    }

    @Test fun lakhGroupingForSouthAsianCurrencies() {
        assertEquals("₹1,23,45,678.90", Money.ofMinor(1_234_567_890L, Currency.INR).format())
        assertEquals("₨2,50,000.00", Money.ofMinor(25_000_000L, Currency.PKR).format())
        assertEquals("৳1,00,000.00", Money.ofMinor(10_000_000L, Currency.BDT).format())
    }

    @Test fun plainStringHasNoGroupingOrSymbol() {
        assertEquals("1234.56", Money.ofMinor(123_456L, Currency.INR).toPlainString())
        assertEquals("1250", Money.ofMinor(1250L, Currency.JPY).toPlainString())
    }

    @Test fun arithmeticInSameCurrency() {
        val a = Money.parse("10.00", Currency.USD)
        val b = Money.parse("3.50", Currency.USD)
        assertEquals("$13.50", (a + b).format())
        assertEquals("$6.50", (a - b).format())
        assertEquals("$10.50", (b * 3).format())
        assertEquals("-$10.00", (-a).format())
    }

    @Test fun arithmeticAcrossCurrenciesIsRejected() {
        val usd = Money.parse("10.00", Currency.USD)
        val inr = Money.parse("10.00", Currency.INR)
        assertFailsWith<IllegalArgumentException> { usd + inr }
        assertFailsWith<IllegalArgumentException> { usd - inr }
        assertFailsWith<IllegalArgumentException> { usd.compareTo(inr) }
    }

    @Test fun formatPadsMinorUnits() {
        assertEquals("$0.05", Money.ofMinor(5, Currency.USD).format())
        assertEquals("$0.50", Money.ofMinor(50, Currency.USD).format())
        assertEquals("$5.00", Money.ofMinor(500, Currency.USD).format())
    }

    @Test fun threeDecimalCurrencies() {
        val m = Money.parse("1.234", Currency.BHD)
        assertEquals(1234L, m.minorUnits)
    }

    @Test fun defaultFallbackCurrencyIsUsd() {
        assertEquals(Currency.USD, Currency.DEFAULT)
    }

    @Test fun lookupByCodeIsCaseInsensitive() {
        assertEquals(Currency.INR, Currency.ofCode("inr"))
        assertEquals(Currency.USD, Currency.ofCode("USD"))
        assertNull(Currency.ofCode("ZZZ"))
    }

    @Test fun amountsThatWouldOverflowMinorUnitsAreRejected() {
        // 2e17 * 100 wraps to a positive Long, so an unchecked parse would accept
        // this as a perfectly ordinary $15,532,559,262,904,483.84.
        assertFailsWith<IllegalArgumentException> {
            Money.parse("200000000000000000", Currency.USD)
        }
    }

    @Test fun malformedInputFailsAsAnIllegalArgument() {
        for (bad in listOf(".50", "1e5", "--5", "+-5", "abc", "1.2.3")) {
            assertFailsWith<IllegalArgumentException>("accepted $bad") {
                Money.parse(bad, Currency.USD)
            }
        }
    }
}
