package app.splitup.shared.domain.usecase

import app.splitup.shared.domain.debt.Debt
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.Settlement
import app.splitup.shared.domain.model.SettlementId
import app.splitup.shared.domain.model.SettlementMethod
import app.splitup.shared.domain.repository.SettlementRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Compute the minimal set of transfers that zero out everyone's balance, then
 * (optionally) persist one or more as Settlements.
 */
class SettleUpUseCase(
    private val settlements: SettlementRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val idGenerator: () -> String,
) {
    /** Pure planning step: what would zero out everyone? */
    fun plan(expenses: List<Expense>): List<Debt> {
        val pairwise = DebtSimplifier.debtsFromExpenses(expenses)
        return DebtSimplifier.simplify(pairwise)
    }

    /** Record a single payment. */
    suspend fun record(
        groupId: GroupId?,
        from: PersonId,
        to: PersonId,
        amount: Money,
        method: SettlementMethod = SettlementMethod.UNSPECIFIED,
        notes: String? = null,
        date: LocalDate? = null,
    ): Settlement {
        val now = clock.now()
        val s = Settlement(
            id = SettlementId(idGenerator()),
            groupId = groupId,
            fromPersonId = from,
            toPersonId = to,
            amount = amount,
            date = date ?: now.toLocalDateTime(timeZone).date,
            method = method,
            notes = notes,
            createdAt = now,
            updatedAt = now,
        )
        settlements.save(s)
        return s
    }
}
