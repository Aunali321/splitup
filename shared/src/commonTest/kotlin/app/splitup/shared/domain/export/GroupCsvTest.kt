package app.splitup.shared.domain.export

import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.ExpenseShare
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.split.SplitStrategy
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class GroupCsvTest {

    private val alice = PersonId("alice")
    private val usd = Currency.USD

    private fun expense(description: String): Expense {
        val cost = Money.parse("10.00", usd)
        return Expense(
            id = ExpenseId("e1"),
            groupId = null,
            description = description,
            cost = cost,
            date = LocalDate.parse("2024-05-01"),
            createdBy = alice,
            splitStrategy = SplitStrategy.Equal(listOf(alice)),
            shares = listOf(ExpenseShare(alice, cost, cost)),
            createdAt = Instant.DISTANT_PAST,
            updatedAt = Instant.DISTANT_PAST,
        )
    }

    private fun person(name: String) =
        Person(id = alice, firstName = name, updatedAt = Instant.DISTANT_PAST)

    @Test fun memberSuppliedTextCannotStartAFormula() {
        val csv = GroupCsv.build(
            expenses = listOf(expense("=HYPERLINK(\"http://evil.example\",\"receipt\")")),
            members = listOf(person("@SUM(A1:A9)")),
        )
        val cells = csv.split("\n").flatMap { it.split(",") }
        assertTrue(
            cells.none { it.trimStart('"').firstOrNull() in listOf('=', '+', '@') },
            "a cell still opens with a formula character:\n$csv",
        )
    }

    @Test fun negativeAmountsStayNumeric() {
        // The defusing must not touch the columns we generate, or every negative
        // balance would import as text.
        val csv = GroupCsv.build(listOf(expense("Dinner")), listOf(person("Alice")))
        assertTrue(csv.contains("0.00"), csv)
        assertTrue(!csv.contains("'-"), "an amount column was quoted as text:\n$csv")
    }

    @Test fun quotingStillFollowsRfc4180() {
        val csv = GroupCsv.build(listOf(expense("Dinner, with \"friends\"")), listOf(person("Alice")))
        assertEquals(1, csv.split("\n").count { it.contains("\"\"friends\"\"") }, csv)
    }
}
