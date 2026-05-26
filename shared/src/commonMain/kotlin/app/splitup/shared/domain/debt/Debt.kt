package app.splitup.shared.domain.debt

import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId

/**
 * A directed debt: [from] owes [amount] to [to].
 * The amount carries its currency; direction is encoded by the (from, to) pair.
 */
data class Debt(
    val from: PersonId,
    val to: PersonId,
    val amount: Money,
) {
    init {
        require(amount.isPositive) { "Debt amount must be positive" }
        require(from != to) { "Self-debt is meaningless" }
    }
}
