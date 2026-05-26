package app.splitup.shared.domain.debt

import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.split.SplitCalculator
import app.splitup.shared.domain.split.SplitStrategy
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test

class DebtSimplifierTest {

    private val alice = PersonId("alice")
    private val bob = PersonId("bob")
    private val carol = PersonId("carol")
    private val dave = PersonId("dave")
    private val now = Instant.fromEpochSeconds(1_735_689_600L) // 2025-01-01

    private fun inr(v: String) = Money.parse(v, Currency.INR)

    private fun expense(
        id: String,
        total: String,
        payers: Map<PersonId, String>,
        participants: List<PersonId>,
        currency: Currency = Currency.INR,
    ): Expense {
        val totalMoney = Money.parse(total, currency)
        val payerMoney = payers.mapValues { Money.parse(it.value, currency) }
        val strategy = SplitStrategy.Equal(participants)
        val shares = SplitCalculator.calculate(totalMoney, payerMoney, strategy)
        return Expense(
            id = ExpenseId(id),
            groupId = null,
            description = id,
            cost = totalMoney,
            date = LocalDate(2025, 1, 1),
            categoryId = CategoryId("uncategorized"),
            createdBy = payers.keys.first(),
            splitStrategy = strategy,
            shares = shares,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test fun `single expense — A pays for B`() {
        val e = expense("e1", "20.00", mapOf(alice to "20.00"), listOf(alice, bob))
        val out = DebtSimplifier.simplify(DebtSimplifier.debtsFromExpenses(listOf(e)))
        out shouldHaveSize 1
        out[0].from shouldBe bob
        out[0].to shouldBe alice
        out[0].amount shouldBe inr("10.00")
    }

    @Test fun `cyclical debts cancel out`() {
        val e1 = expense("e1", "20.00", mapOf(alice to "20.00"), listOf(alice, bob))
        val e2 = expense("e2", "20.00", mapOf(bob to "20.00"), listOf(bob, carol))
        val e3 = expense("e3", "20.00", mapOf(carol to "20.00"), listOf(carol, alice))
        val out = DebtSimplifier.simplify(DebtSimplifier.debtsFromExpenses(listOf(e1, e2, e3)))
        out.shouldHaveSize(0)
    }

    @Test fun `four-way trip simplifies to minimum transfers`() {
        val e1 = expense("hotel", "400.00", mapOf(alice to "400.00"), listOf(alice, bob, carol, dave))
        val e2 = expense("car",   "200.00", mapOf(bob to "200.00"),   listOf(alice, bob, carol, dave))
        val e3 = expense("food",  "120.00", mapOf(carol to "120.00"), listOf(alice, bob, carol, dave))

        val out = DebtSimplifier.simplify(DebtSimplifier.debtsFromExpenses(listOf(e1, e2, e3)))
        val totalDebt = out.sumOf { it.amount.minorUnits }
        totalDebt shouldBe 24000L
        val netFrom = out.groupBy { it.from }.mapValues { it.value.sumOf { d -> d.amount.minorUnits } }
        val netTo = out.groupBy { it.to }.mapValues { it.value.sumOf { d -> d.amount.minorUnits } }
        (netTo[alice] ?: 0L) - (netFrom[alice] ?: 0L) shouldBe 22000L
        (netTo[bob] ?: 0L) - (netFrom[bob] ?: 0L) shouldBe 2000L
        (netTo[carol] ?: 0L) - (netFrom[carol] ?: 0L) shouldBe -6000L
        (netTo[dave] ?: 0L) - (netFrom[dave] ?: 0L) shouldBe -18000L
        (out.size <= 3) shouldBe true
    }

    @Test fun `multiple currencies are simplified independently`() {
        val inrExpense = expense("inr", "20.00", mapOf(alice to "20.00"), listOf(alice, bob), Currency.INR)
        val usdExpense = expense("usd", "20.00", mapOf(bob to "20.00"), listOf(alice, bob), Currency.USD)

        val out = DebtSimplifier.simplify(
            DebtSimplifier.debtsFromExpenses(listOf(inrExpense, usdExpense))
        )
        out.shouldHaveSize(2)
        out.first { it.amount.currency == Currency.INR }.from shouldBe bob
        out.first { it.amount.currency == Currency.USD }.from shouldBe alice
    }
}
