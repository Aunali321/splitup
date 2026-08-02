package app.splitup.shared.domain.usecase

import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.model.next
import app.splitup.shared.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** When [interval] recurs, the instant the occurrence after [date] falls due. */
internal fun nextRepeatAt(interval: RepeatInterval, date: LocalDate, timeZone: TimeZone): Instant? =
    if (interval == RepeatInterval.NEVER) null else interval.next(date).atStartOfDayIn(timeZone)

/**
 * Turns due recurring expenses into real ones. A recurring expense is its own
 * template: each due date spawns a plain copy (same split, new id and date) and
 * the template's [nextRepeatAt] advances past today. Run on app start.
 *
 * Occurrence ids derive from the template and due date, so two devices
 * materializing the same occurrence converge on one row instead of duplicating.
 */
class MaterializeRecurringExpensesUseCase(
    private val expenses: ExpenseRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke() {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val templates = expenses.observeAll().first()
            .filter { it.repeatInterval != RepeatInterval.NEVER && it.nextRepeatAt != null }

        templates.forEach { template ->
            var due = template.nextRepeatAt!!.toLocalDateTime(timeZone).date
            if (due > today) return@forEach
            while (due <= today) {
                expenses.save(
                    template.copy(
                        id = ExpenseId("${template.id.value}@$due"),
                        date = due,
                        repeatInterval = RepeatInterval.NEVER,
                        nextRepeatAt = null,
                        externalSource = null,
                        externalId = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                due = template.repeatInterval.next(due)
            }
            expenses.save(template.copy(nextRepeatAt = due.atStartOfDayIn(timeZone), updatedAt = now))
        }
    }
}
