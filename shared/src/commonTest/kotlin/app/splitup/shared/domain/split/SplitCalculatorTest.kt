package app.splitup.shared.domain.split

import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SplitCalculatorTest {

    private val alice = PersonId("alice")
    private val bob = PersonId("bob")
    private val carol = PersonId("carol")

    private fun inr(v: String) = Money.parse(v, Currency.INR)
    private fun usd(v: String) = Money.parse(v, Currency.USD)

    @Test fun equalSplitBetweenThreeWithNoRemainder() {
        val out = SplitCalculator.calculate(
            total = inr("30.00"),
            payers = mapOf(alice to inr("30.00")),
            strategy = SplitStrategy.Equal(listOf(alice, bob, carol)),
        )
        assertEquals(listOf(inr("10.00"), inr("10.00"), inr("10.00")), out.map { it.owedShare })
        assertEquals(inr("30.00"), out.first { it.personId == alice }.paidShare)
    }

    @Test fun equalSplitDistributesRemainderDeterministicallyById() {
        // 1001 paise / 3 = 333 paise remainder 2 → first 2 sorted ids get +1.
        val out = SplitCalculator.calculate(
            total = inr("10.01"),
            payers = mapOf(alice to inr("10.01")),
            strategy = SplitStrategy.Equal(listOf(carol, alice, bob)),
        )
        assertEquals(inr("3.34"), out.first { it.personId == alice }.owedShare)
        assertEquals(inr("3.34"), out.first { it.personId == bob }.owedShare)
        assertEquals(inr("3.33"), out.first { it.personId == carol }.owedShare)
    }

    @Test fun exactSplitMustSumToTotal() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.calculate(
                total = inr("100.00"),
                payers = mapOf(alice to inr("100.00")),
                strategy = SplitStrategy.Exact(mapOf(alice to inr("50.00"), bob to inr("49.00"))),
            )
        }
    }

    @Test fun percentSplitRoundsAndReconcilesToTotal() {
        val out = SplitCalculator.calculate(
            total = inr("100.00"),
            payers = mapOf(alice to inr("100.00")),
            strategy = SplitStrategy.Percent(mapOf(alice to 3334, bob to 3333, carol to 3333)),
        )
        assertEquals(inr("100.00"), out.fold(Money.zero(Currency.INR)) { acc, s -> acc + s.owedShare })
        assertEquals(inr("33.34"), out.first { it.personId == alice }.owedShare)
    }

    @Test fun percentSplitMustSumToHundred() {
        assertFailsWith<IllegalArgumentException> {
            SplitStrategy.Percent(mapOf(alice to 5000, bob to 4000))
        }
    }

    @Test fun sharesSplitInRatio() {
        val out = SplitCalculator.calculate(
            total = inr("60.00"),
            payers = mapOf(alice to inr("60.00")),
            strategy = SplitStrategy.Shares(mapOf(alice to 1, bob to 2, carol to 3)),
        )
        assertEquals(inr("10.00"), out.first { it.personId == alice }.owedShare)
        assertEquals(inr("20.00"), out.first { it.personId == bob }.owedShare)
        assertEquals(inr("30.00"), out.first { it.personId == carol }.owedShare)
    }

    @Test fun adjustmentSplitAddsExtraOnTopOfEqualShare() {
        // 30.00 total, bob owes ₹3 extra, remaining ₹27 split 3 ways.
        val out = SplitCalculator.calculate(
            total = inr("30.00"),
            payers = mapOf(alice to inr("30.00")),
            strategy = SplitStrategy.Adjustment(
                participants = listOf(alice, bob, carol),
                adjustments = mapOf(bob to inr("3.00")),
            ),
        )
        assertEquals(inr("9.00"), out.first { it.personId == alice }.owedShare)
        assertEquals(inr("12.00"), out.first { it.personId == bob }.owedShare)
        assertEquals(inr("9.00"), out.first { it.personId == carol }.owedShare)
        assertEquals(inr("30.00"), out.fold(Money.zero(Currency.INR)) { acc, s -> acc + s.owedShare })
    }

    @Test fun multiplePayersSplitEqually() {
        val out = SplitCalculator.calculate(
            total = inr("90.00"),
            payers = mapOf(alice to inr("50.00"), bob to inr("40.00")),
            strategy = SplitStrategy.Equal(listOf(alice, bob, carol)),
        )
        out.first { it.personId == alice }.let {
            assertEquals(inr("50.00"), it.paidShare)
            assertEquals(inr("30.00"), it.owedShare)
        }
        out.first { it.personId == bob }.let {
            assertEquals(inr("40.00"), it.paidShare)
            assertEquals(inr("30.00"), it.owedShare)
        }
        out.first { it.personId == carol }.let {
            assertEquals(Money.zero(Currency.INR), it.paidShare)
            assertEquals(inr("30.00"), it.owedShare)
        }
    }

    @Test fun payerSumsMustMatchTotal() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.calculate(
                total = inr("100.00"),
                payers = mapOf(alice to inr("50.00")),
                strategy = SplitStrategy.Equal(listOf(alice, bob)),
            )
        }
    }

    @Test fun payerInWrongCurrencyIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.calculate(
                total = inr("100.00"),
                payers = mapOf(alice to usd("100.00")),
                strategy = SplitStrategy.Equal(listOf(alice, bob)),
            )
        }
    }

    @Test fun outputIsSortedByPersonId() {
        val out = SplitCalculator.calculate(
            total = inr("30.00"),
            payers = mapOf(carol to inr("30.00")),
            strategy = SplitStrategy.Equal(listOf(carol, bob, alice)),
        )
        assertTrue(out.map { it.personId.value } == out.map { it.personId.value }.sorted())
    }
}
