package app.splitup.shared.domain.split

import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Rounding, degenerate weights and duplicate participants — the ways a split can lose money. */
class SplitRoundingTest {

    private val alice = PersonId("alice")
    private val bob = PersonId("bob")
    private val carol = PersonId("carol")

    private fun inr(v: String) = Money.parse(v, Currency.INR)

    @Test fun zeroPercentParticipantNeverAbsorbsTheRemainder() {
        // 1001 paise, 0/50/50. alice sorts first, so a naive remainder pass would
        // charge her a paise for a 0% share.
        val out = SplitCalculator.calculate(
            total = inr("10.01"),
            payers = mapOf(bob to inr("10.01")),
            strategy = SplitStrategy.Percent(mapOf(alice to 0, bob to 5_000, carol to 5_000)),
        )
        assertEquals(inr("0.00"), out.first { it.personId == alice }.owedShare)
        assertEquals(inr("5.01"), out.first { it.personId == bob }.owedShare)
        assertEquals(inr("5.00"), out.first { it.personId == carol }.owedShare)
    }

    @Test fun duplicateParticipantIsRejectedRatherThanSilentlyCollapsed() {
        // Collapsing the duplicate while still dividing by 3 loses a third of the total.
        assertFailsWith<IllegalArgumentException> {
            SplitStrategy.Equal(listOf(alice, alice, bob))
        }
        assertFailsWith<IllegalArgumentException> {
            SplitStrategy.Adjustment(listOf(alice, alice, bob), emptyMap())
        }
    }

    @Test fun adjustmentCannotDriveAShareNegative() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.calculate(
                total = inr("30.00"),
                payers = mapOf(bob to inr("30.00")),
                strategy = SplitStrategy.Adjustment(
                    participants = listOf(alice, bob),
                    adjustments = mapOf(alice to inr("-100.00")),
                ),
            )
        }
    }

    @Test fun everyStrategyConservesTheTotalAcrossAwkwardAmounts() {
        val strategies = listOf(
            SplitStrategy.Equal(listOf(alice, bob, carol)),
            SplitStrategy.Percent(mapOf(alice to 3_333, bob to 3_333, carol to 3_334)),
            SplitStrategy.Shares(mapOf(alice to 1, bob to 1, carol to 1)),
            SplitStrategy.Adjustment(listOf(alice, bob, carol), mapOf(alice to inr("0.01"))),
        )
        // From 2 paise up: a 1-paise total leaves an Adjustment split nothing to divide.
        for (minor in 2L..40L) {
            val total = Money.ofMinor(minor, Currency.INR)
            for (strategy in strategies) {
                val out = SplitCalculator.calculate(total, mapOf(alice to total), strategy)
                val owed = out.fold(Money.zero(Currency.INR)) { acc, s -> acc + s.owedShare }
                assertEquals(total, owed, "$strategy lost money at $total")
                assertTrue(out.none { it.owedShare.isNegative }, "$strategy went negative at $total")
            }
        }
    }
}
