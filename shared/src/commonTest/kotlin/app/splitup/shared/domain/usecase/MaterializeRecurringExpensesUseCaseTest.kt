package app.splitup.shared.domain.usecase

import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.ExpenseShare
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.split.SplitStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class MaterializeRecurringExpensesUseCaseTest {

    private val alice = PersonId("alice")
    private val usd = Currency.USD

    private class FakeExpenses : ExpenseRepository {
        val rows = MutableStateFlow<Map<ExpenseId, Expense>>(emptyMap())
        override fun observeAll(): Flow<List<Expense>> = rows.map { it.values.toList() }
        override suspend fun save(expense: Expense) { rows.value += expense.id to expense }
        override suspend fun get(id: ExpenseId): Expense? = rows.value[id]

        override fun observeInGroup(groupId: GroupId): Flow<List<Expense>> = observeAll()
        override fun observeNonGroup(): Flow<List<Expense>> = observeAll()
        override fun observeWithFriend(friendId: PersonId): Flow<List<Expense>> = observeAll()
        override fun observeRecent(limit: Int): Flow<List<Expense>> = observeAll()
        override fun observeFeed(limit: Int): Flow<List<Expense>> = observeAll()
        override fun observe(id: ExpenseId): Flow<Expense?> = rows.map { it[id] }
        override suspend fun softDelete(id: ExpenseId) {
            rows.value[id]?.let { rows.value += id to it.copy(deletedAt = Instant.DISTANT_PAST) }
        }
        override suspend fun findByExternalId(source: String, externalId: String): Expense? = null
        override suspend fun search(query: String, from: LocalDate?, to: LocalDate?): List<Expense> =
            emptyList()
    }

    private fun template(start: LocalDate, interval: RepeatInterval): Expense {
        val cost = Money.parse("10.00", usd)
        return Expense(
            id = ExpenseId("rent"),
            groupId = null,
            description = "Rent",
            cost = cost,
            date = start,
            createdBy = alice,
            splitStrategy = SplitStrategy.Equal(listOf(alice)),
            shares = listOf(ExpenseShare(alice, cost, cost)),
            repeatInterval = interval,
            nextRepeatAt = nextRepeatAt(interval, start),
            createdAt = Instant.DISTANT_PAST,
            updatedAt = Instant.DISTANT_PAST,
        )
    }

    /** Midday in [zone], so the local date is unambiguously [date]. */
    private fun clockAt(date: LocalDate, zone: TimeZone) = object : Clock {
        override fun now(): Instant = date.atStartOfDayIn(zone) + 12.hours
    }

    private fun occurrenceDates(repo: FakeExpenses): List<String> =
        repo.rows.value.keys.map { it.value }.filter { it.startsWith("rent@") }
            .map { it.removePrefix("rent@") }.sorted()

    @Test fun monthlySeriesFromMonthEndDoesNotDriftOffTheEnd() = runTest {
        val repo = FakeExpenses()
        val start = LocalDate.parse("2024-01-31")
        repo.save(template(start, RepeatInterval.MONTHLY))

        MaterializeRecurringExpensesUseCase(
            expenses = repo,
            clock = clockAt(LocalDate.parse("2024-06-01"), TimeZone.UTC),
            timeZone = TimeZone.UTC,
        ).invoke()

        // Each occurrence is measured from Jan 31, so May lands on the 31st again
        // instead of being pinned to Feb's clamped 29th for the rest of the series.
        assertEquals(
            listOf("2024-02-29", "2024-03-31", "2024-04-30", "2024-05-31"),
            occurrenceDates(repo),
        )
    }

    @Test fun occurrenceIdsAreTheSameWhicheverZoneTheDeviceIsIn() = runTest {
        val start = LocalDate.parse("2024-03-01")
        val today = LocalDate.parse("2024-05-02")

        suspend fun run(zone: TimeZone): List<String> {
            val repo = FakeExpenses()
            repo.save(template(start, RepeatInterval.MONTHLY))
            MaterializeRecurringExpensesUseCase(repo, clockAt(today, zone), zone).invoke()
            return occurrenceDates(repo)
        }

        assertEquals(run(TimeZone.UTC), run(TimeZone.of("Asia/Kolkata")))
        assertEquals(run(TimeZone.UTC), run(TimeZone.of("America/Los_Angeles")))
    }

    @Test fun rerunningLeavesAlreadyMaterialisedOccurrencesUntouched() = runTest {
        val repo = FakeExpenses()
        val start = LocalDate.parse("2024-01-01")
        repo.save(template(start, RepeatInterval.MONTHLY))
        val use = MaterializeRecurringExpensesUseCase(
            expenses = repo,
            clock = clockAt(LocalDate.parse("2024-04-15"), TimeZone.UTC),
            timeZone = TimeZone.UTC,
        )
        use()
        val before = occurrenceDates(repo)

        // A user deletes one occurrence and renames another; a later run must respect both.
        repo.softDelete(ExpenseId("rent@2024-03-01"))
        repo.save(repo.get(ExpenseId("rent@2024-02-01"))!!.copy(description = "Rent (adjusted)"))
        use()

        assertEquals(before, occurrenceDates(repo))
        assertEquals("Rent (adjusted)", repo.get(ExpenseId("rent@2024-02-01"))!!.description)
        assertEquals(Instant.DISTANT_PAST, repo.get(ExpenseId("rent@2024-03-01"))!!.deletedAt)
    }
}
