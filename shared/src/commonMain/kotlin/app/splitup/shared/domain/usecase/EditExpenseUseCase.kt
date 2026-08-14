package app.splitup.shared.domain.usecase

import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.split.SplitCalculator
import app.splitup.shared.domain.split.SplitStrategy
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

/**
 * Re-runs the split engine for an existing expense and saves it in place, preserving
 * identity and provenance (id, group, creator, createdAt, category, notes). Used by
 * the Add-Expense flow when it was opened to edit rather than create.
 */
class EditExpenseUseCase(
    private val expenses: ExpenseRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(input: Input): Expense {
        val shares = SplitCalculator.calculate(
            total = input.total,
            payers = input.payers,
            strategy = input.strategy,
        )
        val updated = input.original.copy(
            description = input.description,
            cost = input.total,
            date = input.date,
            categoryId = input.categoryId,
            notes = input.notes,
            splitStrategy = input.strategy,
            shares = shares,
            repeatInterval = input.repeatInterval,
            // Only a schedule change restarts the series. Recomputing on every edit
            // would rewind it to the second occurrence and re-create everything since.
            nextRepeatAt = if (
                input.repeatInterval == input.original.repeatInterval &&
                input.date == input.original.date
            ) {
                input.original.nextRepeatAt
            } else {
                nextRepeatAt(input.repeatInterval, input.date)
            },
            receiptUrl = input.receiptUrl,
            updatedAt = clock.now(),
        )
        expenses.save(updated)
        return updated
    }

    data class Input(
        val original: Expense,
        val description: String,
        val total: Money,
        val date: LocalDate,
        val categoryId: CategoryId,
        val notes: String?,
        val payers: Map<PersonId, Money>,
        val strategy: SplitStrategy,
        val repeatInterval: RepeatInterval,
        val receiptUrl: String?,
    )
}
