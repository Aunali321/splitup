package app.splitup.shared.domain.export

import app.splitup.shared.domain.model.DefaultCategories
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person

/**
 * Splitwise-compatible CSV export of a group's expenses: one row per expense
 * with a net column (paid − owed) per member. A "Total balance" footer row is
 * appended when every expense shares one currency; in a mixed-currency group
 * the per-member sums would be meaningless, so it is omitted.
 */
object GroupCsv {

    fun build(expenses: List<Expense>, members: List<Person>): String {
        val rows = buildList {
            add(
                listOf("Date", "Description", "Category", "Cost", "Currency") +
                    members.map { defuse(it.displayName) },
            )
            expenses.forEach { e ->
                add(
                    listOf(
                        e.date.toString(),
                        defuse(e.description),
                        defuse(DefaultCategories.get(e.categoryId).name),
                        e.cost.toPlainString(),
                        e.currencyCode,
                    ) + members.map { m -> e.balanceFor(m.id).toPlainString() },
                )
            }
            val currencies = expenses.map { it.currency }.distinct()
            if (currencies.size == 1) {
                val zero = Money.zero(currencies.single())
                add(
                    listOf("", "Total balance", "", "", currencies.single().code) +
                        members.map { m ->
                            expenses.fold(zero) { acc, e -> acc + e.balanceFor(m.id) }.toPlainString()
                        },
                )
            }
        }
        return rows.joinToString("\n") { row -> row.joinToString(",") { escape(it) } }
    }

    /**
     * Spreadsheet apps evaluate a cell starting with =, +, -, @, tab or CR as a
     * formula. Descriptions and display names are written by other group members,
     * so they are prefixed with an apostrophe to force literal text. Only applied
     * to member-supplied fields — the amount columns are ours and may start with
     * a minus legitimately.
     */
    private fun defuse(field: String): String =
        if (field.isNotEmpty() && field[0] in "=+-@\t\r") "'$field" else field

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
}
