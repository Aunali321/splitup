package app.splitup.ui.util

import app.splitup.shared.domain.model.Expense

/** Case-insensitive description/notes match used by the scoped expense searches. */
fun List<Expense>.matching(query: String): List<Expense> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter {
        it.description.contains(q, ignoreCase = true) || it.notes?.contains(q, ignoreCase = true) == true
    }
}
