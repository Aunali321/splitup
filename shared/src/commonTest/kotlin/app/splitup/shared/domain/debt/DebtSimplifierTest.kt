package app.splitup.shared.domain.debt

import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.ExpenseShare
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.split.SplitCalculator
import app.splitup.shared.domain.split.SplitStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

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
            createdBy = payers.keys.first(),
            splitStrategy = strategy,
            shares = shares,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun payment(id: String, from: PersonId, to: PersonId, amount: String): Expense {
        val money = inr(amount)
        val zero = Money.zero(money.currency)
        return Expense(
            id = ExpenseId(id),
            groupId = null,
            description = "Payment",
            cost = money,
            date = LocalDate(2025, 1, 2),
            createdBy = from,
            splitStrategy = SplitStrategy.Exact(mapOf(to to money)),
            shares = listOf(
                ExpenseShare(personId = from, paidShare = money, owedShare = zero),
                ExpenseShare(personId = to, paidShare = zero, owedShare = money),
            ),
            isPayment = true,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test fun singleExpenseAPaysForB() {
        val e = expense("e1", "20.00", mapOf(alice to "20.00"), listOf(alice, bob))
        val out = DebtSimplifier.simplify(DebtSimplifier.debtsFromExpenses(listOf(e)))
        assertEquals(1, out.size)
        assertEquals(bob, out[0].from)
        assertEquals(alice, out[0].to)
        assertEquals(inr("10.00"), out[0].amount)
    }

    @Test fun cyclicalDebtsCancelOut() {
        val e1 = expense("e1", "20.00", mapOf(alice to "20.00"), listOf(alice, bob))
        val e2 = expense("e2", "20.00", mapOf(bob to "20.00"), listOf(bob, carol))
        val e3 = expense("e3", "20.00", mapOf(carol to "20.00"), listOf(carol, alice))
        val out = DebtSimplifier.simplify(DebtSimplifier.debtsFromExpenses(listOf(e1, e2, e3)))
        assertEquals(emptyList(), out)
    }

    @Test fun fourWayTripSimplifiesToMinimumTransfers() {
        val e1 = expense("hotel", "400.00", mapOf(alice to "400.00"), listOf(alice, bob, carol, dave))
        val e2 = expense("car", "200.00", mapOf(bob to "200.00"), listOf(alice, bob, carol, dave))
        val e3 = expense("food", "120.00", mapOf(carol to "120.00"), listOf(alice, bob, carol, dave))

        val out = DebtSimplifier.simplify(DebtSimplifier.debtsFromExpenses(listOf(e1, e2, e3)))
        assertEquals(24000L, out.sumOf { it.amount.minorUnits })
        val netFrom = out.groupBy { it.from }.mapValues { it.value.sumOf { d -> d.amount.minorUnits } }
        val netTo = out.groupBy { it.to }.mapValues { it.value.sumOf { d -> d.amount.minorUnits } }
        assertEquals(22000L, (netTo[alice] ?: 0L) - (netFrom[alice] ?: 0L))
        assertEquals(2000L, (netTo[bob] ?: 0L) - (netFrom[bob] ?: 0L))
        assertEquals(-6000L, (netTo[carol] ?: 0L) - (netFrom[carol] ?: 0L))
        assertEquals(-18000L, (netTo[dave] ?: 0L) - (netFrom[dave] ?: 0L))
        assertTrue(out.size <= 3)
    }

    @Test fun multipleCurrenciesAreSimplifiedIndependently() {
        val inrExpense = expense("inr", "20.00", mapOf(alice to "20.00"), listOf(alice, bob), Currency.INR)
        val usdExpense = expense("usd", "20.00", mapOf(bob to "20.00"), listOf(alice, bob), Currency.USD)

        val out = DebtSimplifier.simplify(
            DebtSimplifier.debtsFromExpenses(listOf(inrExpense, usdExpense)),
        )
        assertEquals(2, out.size)
        assertEquals(bob, out.first { it.amount.currency == Currency.INR }.from)
        assertEquals(alice, out.first { it.amount.currency == Currency.USD }.from)
    }

    @Test fun balancesTowardIsSignedPerCounterparty() {
        val e = expense("e1", "20.00", mapOf(alice to "20.00"), listOf(alice, bob))
        assertEquals(mapOf(Currency.INR to inr("10.00")), DebtSimplifier.balancesToward(listOf(e), alice)[bob])
        assertEquals(mapOf(Currency.INR to inr("-10.00")), DebtSimplifier.balancesToward(listOf(e), bob)[alice])
        assertEquals(null, DebtSimplifier.balancesToward(listOf(e), alice)[carol])
    }

    @Test fun netOfDropsZeroAndUninvolved() {
        val e = expense("e1", "20.00", mapOf(alice to "20.00"), listOf(alice, bob))
        assertEquals(mapOf(Currency.INR to inr("10.00")), DebtSimplifier.netOf(listOf(e), alice))
        assertEquals(mapOf(Currency.INR to inr("-10.00")), DebtSimplifier.netOf(listOf(e), bob))
        assertEquals(emptyMap(), DebtSimplifier.netOf(listOf(e), carol))
    }

    @Test fun paymentSettlesTheDebt() {
        val e = expense("e1", "20.00", mapOf(alice to "20.00"), listOf(alice, bob))
        val p = payment("p1", from = bob, to = alice, amount = "10.00")
        assertEquals(emptyMap(), DebtSimplifier.netOf(listOf(e, p), alice))
        assertEquals(emptyMap(), DebtSimplifier.netOf(listOf(e, p), bob))
    }

    @Test fun deletedExpensesAreIgnored() {
        val e = expense("e1", "20.00", mapOf(alice to "20.00"), listOf(alice, bob)).copy(deletedAt = now)
        assertEquals(emptyList(), DebtSimplifier.debtsFromExpenses(listOf(e)))
    }
}
