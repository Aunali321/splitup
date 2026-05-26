package app.splitup.shared.domain.split

import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SplitCalculatorTest {

    private val alice = PersonId("alice")
    private val bob = PersonId("bob")
    private val carol = PersonId("carol")

    private fun inr(v: String) = Money.parse(v, Currency.INR)
    private fun usd(v: String) = Money.parse(v, Currency.USD)

    @Test fun `equal split between three with no remainder`() {
        val out = SplitCalculator.calculate(
            total = inr("30.00"),
            payers = mapOf(alice to inr("30.00")),
            strategy = SplitStrategy.Equal(listOf(alice, bob, carol)),
        )
        out.map { it.owedShare } shouldContainExactlyInAnyOrder listOf(inr("10.00"), inr("10.00"), inr("10.00"))
        out.first { it.personId == alice }.paidShare shouldBe inr("30.00")
    }

    @Test fun `equal split distributes remainder deterministically by id`() {
        // 1001 paise / 3 = 333 paise remainder 2 → first 2 sorted ids get +1.
        val out = SplitCalculator.calculate(
            total = inr("10.01"),
            payers = mapOf(alice to inr("10.01")),
            strategy = SplitStrategy.Equal(listOf(carol, alice, bob)),
        )
        out.first { it.personId == alice }.owedShare shouldBe inr("3.34")
        out.first { it.personId == bob }.owedShare shouldBe inr("3.34")
        out.first { it.personId == carol }.owedShare shouldBe inr("3.33")
    }

    @Test fun `exact split must sum to total`() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.calculate(
                total = inr("100.00"),
                payers = mapOf(alice to inr("100.00")),
                strategy = SplitStrategy.Exact(mapOf(alice to inr("50.00"), bob to inr("49.00"))),
            )
        }
    }

    @Test fun `percent split rounds and reconciles to total`() {
        val out = SplitCalculator.calculate(
            total = inr("100.00"),
            payers = mapOf(alice to inr("100.00")),
            strategy = SplitStrategy.Percent(mapOf(alice to 3334, bob to 3333, carol to 3333)),
        )
        out.fold(Money.zero(Currency.INR)) { acc, s -> acc + s.owedShare } shouldBe inr("100.00")
        out.first { it.personId == alice }.owedShare shouldBe inr("33.34")
    }

    @Test fun `percent split must sum to 100%`() {
        assertFailsWith<IllegalArgumentException> {
            SplitStrategy.Percent(mapOf(alice to 5000, bob to 4000))
        }
    }

    @Test fun `shares split 1-2-3 ratio of 60 rupees`() {
        val out = SplitCalculator.calculate(
            total = inr("60.00"),
            payers = mapOf(alice to inr("60.00")),
            strategy = SplitStrategy.Shares(mapOf(alice to 1, bob to 2, carol to 3)),
        )
        out.first { it.personId == alice }.owedShare shouldBe inr("10.00")
        out.first { it.personId == bob }.owedShare shouldBe inr("20.00")
        out.first { it.personId == carol }.owedShare shouldBe inr("30.00")
    }

    @Test fun `adjustment split adds extra owed amount on top of equal share`() {
        // 30.00 total, bob owes ₹3 extra ("had an extra drink"), remaining ₹27 split 3 ways
        val out = SplitCalculator.calculate(
            total = inr("30.00"),
            payers = mapOf(alice to inr("30.00")),
            strategy = SplitStrategy.Adjustment(
                participants = listOf(alice, bob, carol),
                adjustments = mapOf(bob to inr("3.00")),
            ),
        )
        out.first { it.personId == alice }.owedShare shouldBe inr("9.00")
        out.first { it.personId == bob }.owedShare shouldBe inr("12.00")
        out.first { it.personId == carol }.owedShare shouldBe inr("9.00")
        out.fold(Money.zero(Currency.INR)) { acc, s -> acc + s.owedShare } shouldBe inr("30.00")
    }

    @Test fun `multiple payers split equally`() {
        val out = SplitCalculator.calculate(
            total = inr("90.00"),
            payers = mapOf(alice to inr("50.00"), bob to inr("40.00")),
            strategy = SplitStrategy.Equal(listOf(alice, bob, carol)),
        )
        out.first { it.personId == alice }.let {
            it.paidShare shouldBe inr("50.00")
            it.owedShare shouldBe inr("30.00")
        }
        out.first { it.personId == bob }.let {
            it.paidShare shouldBe inr("40.00")
            it.owedShare shouldBe inr("30.00")
        }
        out.first { it.personId == carol }.let {
            it.paidShare shouldBe Money.zero(Currency.INR)
            it.owedShare shouldBe inr("30.00")
        }
    }

    @Test fun `payer sums must match total`() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.calculate(
                total = inr("100.00"),
                payers = mapOf(alice to inr("50.00")),
                strategy = SplitStrategy.Equal(listOf(alice, bob)),
            )
        }
    }

    @Test fun `payer in wrong currency is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            SplitCalculator.calculate(
                total = inr("100.00"),
                payers = mapOf(alice to usd("100.00")),
                strategy = SplitStrategy.Equal(listOf(alice, bob)),
            )
        }
    }
}
