package app.splitup.shared.domain.usecase

import app.splitup.shared.domain.debt.Debt
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.ExpenseShare
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.split.SplitStrategy
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Compute the minimal set of transfers that zero out everyone's balance, then
 * persist recorded payments. A payment is stored as a payment-type [Expense]
 * (payer covers the full amount, recipient owes it) so it flows through the
 * same balance computation as every other expense — the same shape imported
 * Splitwise payments already use.
 */
class SettleUpUseCase(
    private val expenses: ExpenseRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val idGenerator: () -> String,
) {
    /**
     * Pure planning step: what would zero out everyone? [simplify] mirrors the
     * group's simplify-debts setting — off keeps who-owes-whom pairwise.
     */
    fun plan(expenses: List<Expense>, simplify: Boolean = true): List<Debt> {
        val pairwise = DebtSimplifier.debtsFromExpenses(expenses)
        return if (simplify) DebtSimplifier.simplify(pairwise) else pairwise
    }

    /** Record a single payment from [from] to [to], entered by [recordedBy]. */
    suspend fun record(
        groupId: GroupId?,
        from: PersonId,
        to: PersonId,
        amount: Money,
        recordedBy: PersonId,
        notes: String? = null,
        date: LocalDate? = null,
    ): Expense {
        require(from != to) { "Payment endpoints must differ" }
        require(amount.isPositive) { "Payment amount must be positive" }
        val now = clock.now()
        val payment = Expense(
            id = ExpenseId(idGenerator()),
            groupId = groupId,
            description = "Payment",
            cost = amount,
            date = date ?: now.toLocalDateTime(timeZone).date,
            notes = notes,
            createdBy = recordedBy,
            splitStrategy = SplitStrategy.Exact(mapOf(to to amount)),
            shares = listOf(
                ExpenseShare(personId = from, paidShare = amount, owedShare = Money.zero(amount.currency)),
                ExpenseShare(personId = to, paidShare = Money.zero(amount.currency), owedShare = amount),
            ),
            isPayment = true,
            createdAt = now,
            updatedAt = now,
        )
        expenses.save(payment)
        return payment
    }
}
