package app.splitup.shared.domain.usecase

import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.model.occurrence
import app.splitup.shared.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * A due date carried in an [Instant] column. Anchored to UTC midnight rather than
 * the device's zone so that every device reads back the same calendar date — the
 * occurrence id is derived from it, and a zone-dependent date would let two
 * devices mint two ids for one occurrence.
 */
private val SERIES_ZONE = TimeZone.UTC

private fun LocalDate.asDueDate(): Instant = atStartOfDayIn(SERIES_ZONE)

private fun Instant.dueDate(): LocalDate = toLocalDateTime(SERIES_ZONE).date

/** When [interval] recurs, the instant the occurrence after [date] falls due. */
internal fun nextRepeatAt(interval: RepeatInterval, date: LocalDate): Instant? =
    if (interval == RepeatInterval.NEVER) null else interval.occurrence(date, 1).asDueDate()

/**
 * Turns due recurring expenses into real ones. A recurring expense is its own
 * template: each due date spawns a plain copy (same split, new id and date) and
 * the template's [nextRepeatAt] advances past today. Run on app start.
 *
 * Occurrence ids derive from the template and due date, so two devices
 * materializing the same occurrence converge on one row instead of duplicating.
 * Materialization is strictly additive: an occurrence that already exists is
 * left alone, so edits and deletions a user made to it survive later runs.
 */
class MaterializeRecurringExpensesUseCase(
    private val expenses: ExpenseRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke() {
        val now = clock.now()
        // Due-ness is judged against the user's own calendar, even though the
        // series itself is anchored in UTC.
        val today = now.toLocalDateTime(timeZone).date
        val all = expenses.observeAll().first()
        val existingIds = all.mapTo(mutableSetOf()) { it.id }
        val templates = all.filter {
            it.repeatInterval != RepeatInterval.NEVER && it.nextRepeatAt != null
        }

        templates.forEach { template ->
            val resumeAt = template.nextRepeatAt!!.dueDate()
            if (resumeAt > today) return@forEach

            var n = 1
            var due = template.repeatInterval.occurrence(template.date, n)
            while (due <= today) {
                if (due >= resumeAt) {
                    val id = ExpenseId("${template.id.value}@$due")
                    if (id !in existingIds) {
                        expenses.save(
                            template.copy(
                                id = id,
                                date = due,
                                repeatInterval = RepeatInterval.NEVER,
                                nextRepeatAt = null,
                                externalSource = null,
                                externalId = null,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    }
                }
                n++
                due = template.repeatInterval.occurrence(template.date, n)
            }
            expenses.save(template.copy(nextRepeatAt = due.asDueDate(), updatedAt = now))
        }
    }
}
