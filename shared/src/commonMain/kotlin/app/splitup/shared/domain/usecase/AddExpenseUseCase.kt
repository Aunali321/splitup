package app.splitup.shared.domain.usecase

import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.split.SplitCalculator
import app.splitup.shared.domain.split.SplitStrategy
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Validates inputs, runs the split engine, persists the resulting Expense.
 */
class AddExpenseUseCase(
    private val expenses: ExpenseRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val idGenerator: () -> String,
) {
    suspend operator fun invoke(input: Input): Expense {
        val shares = SplitCalculator.calculate(
            total = input.total,
            payers = input.payers,
            strategy = input.strategy,
        )
        val now = clock.now()
        val expense = Expense(
            id = ExpenseId(idGenerator()),
            groupId = input.groupId,
            description = input.description,
            cost = input.total,
            date = input.date,
            categoryId = input.categoryId,
            notes = input.notes,
            createdBy = input.createdBy,
            splitStrategy = input.strategy,
            shares = shares,
            repeatInterval = input.repeatInterval,
            nextRepeatAt = nextRepeatAt(input.repeatInterval, input.date, timeZone),
            receiptUrl = input.receiptUrl,
            createdAt = now,
            updatedAt = now,
        )
        expenses.save(expense)
        return expense
    }

    data class Input(
        val groupId: GroupId?,
        val description: String,
        val total: Money,
        val date: LocalDate,
        val categoryId: CategoryId = CategoryId("uncategorized"),
        val notes: String? = null,
        val createdBy: PersonId,
        val payers: Map<PersonId, Money>,
        val strategy: SplitStrategy,
        val repeatInterval: RepeatInterval = RepeatInterval.NEVER,
        val receiptUrl: String? = null,
    )
}
