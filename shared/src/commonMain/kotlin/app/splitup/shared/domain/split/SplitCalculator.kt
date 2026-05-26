package app.splitup.shared.domain.split

import app.splitup.shared.domain.model.ExpenseShare
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId

/**
 * Apply a [SplitStrategy] to a total + payers, producing the per-person
 * (paid, owed) breakdown for an Expense. Rounding remainder is distributed
 * in ascending PersonId order so output is stable across runs (sync-safe).
 */
object SplitCalculator {

    fun calculate(
        total: Money,
        payers: Map<PersonId, Money>,
        strategy: SplitStrategy,
    ): List<ExpenseShare> {
        require(total.isPositive) { "Total must be positive (got $total)" }
        require(payers.isNotEmpty()) { "At least one payer required" }
        require(payers.values.all { it.currency == total.currency }) {
            "All payer amounts must be in ${total.currency.code}"
        }
        val zero = Money.zero(total.currency)
        val paidSum = payers.values.fold(zero) { acc, m -> acc + m }
        require(paidSum == total) { "Payer amounts sum to $paidSum but total is $total" }

        val owedByPerson = when (strategy) {
            is SplitStrategy.Equal -> equal(total, strategy.participants)
            is SplitStrategy.Exact -> exact(total, strategy.amounts)
            is SplitStrategy.Percent -> percent(total, strategy.basisPoints)
            is SplitStrategy.Shares -> shares(total, strategy.shares)
            is SplitStrategy.Adjustment -> adjustment(total, strategy.participants, strategy.adjustments)
        }

        val allIds = (payers.keys + owedByPerson.keys).toSortedSet(compareBy { it.value })
        return allIds.map { id ->
            ExpenseShare(
                personId = id,
                paidShare = payers[id] ?: zero,
                owedShare = owedByPerson[id] ?: zero,
            )
        }
    }

    private fun equal(total: Money, participants: List<PersonId>): Map<PersonId, Money> {
        val sorted = participants.sortedBy { it.value }
        val n = sorted.size.toLong()
        val per = total.minorUnits / n
        val remainder = total.minorUnits - per * n
        return sorted.mapIndexed { i, id ->
            id to Money.ofMinor(per + if (i < remainder) 1L else 0L, total.currency)
        }.toMap()
    }

    private fun exact(total: Money, amounts: Map<PersonId, Money>): Map<PersonId, Money> {
        require(amounts.values.all { it.currency == total.currency }) {
            "Exact amounts must be in ${total.currency.code}"
        }
        val zero = Money.zero(total.currency)
        val sum = amounts.values.fold(zero) { acc, m -> acc + m }
        require(sum == total) { "Exact amounts sum to $sum, expected $total" }
        require(amounts.values.all { !it.isNegative }) { "Exact amounts must be non-negative" }
        return amounts
    }

    private fun percent(total: Money, bp: Map<PersonId, Int>): Map<PersonId, Money> {
        val sorted = bp.toSortedMap(compareBy { it.value })
        val raw = sorted.mapValues { (_, basis) -> total.minorUnits * basis / SplitStrategy.Percent.TOTAL_BP }
        val allocated = raw.values.sum()
        val remainder = total.minorUnits - allocated
        return distributeRemainder(raw, remainder).mapValues { Money.ofMinor(it.value, total.currency) }
    }

    private fun shares(total: Money, parts: Map<PersonId, Int>): Map<PersonId, Money> {
        val sorted = parts.toSortedMap(compareBy { it.value })
        val totalShares = sorted.values.sum().toLong()
        val raw = sorted.mapValues { (_, n) -> total.minorUnits * n / totalShares }
        val allocated = raw.values.sum()
        val remainder = total.minorUnits - allocated
        return distributeRemainder(raw, remainder).mapValues { Money.ofMinor(it.value, total.currency) }
    }

    private fun adjustment(
        total: Money,
        participants: List<PersonId>,
        adjustments: Map<PersonId, Money>,
    ): Map<PersonId, Money> {
        require(adjustments.values.all { it.currency == total.currency }) {
            "Adjustments must be in ${total.currency.code}"
        }
        val zero = Money.zero(total.currency)
        val sumAdj = adjustments.values.fold(zero) { acc, m -> acc + m }
        require(sumAdj.minorUnits < total.minorUnits) {
            "Adjustments ($sumAdj) cannot meet or exceed the total ($total)"
        }
        val remaining = total - sumAdj
        require(!remaining.isNegative) { "Adjustments exceed total" }
        val equalPart = equal(remaining, participants)
        return participants.associateWith { id ->
            (equalPart[id] ?: zero) + (adjustments[id] ?: zero)
        }
    }

    private fun distributeRemainder(raw: Map<PersonId, Long>, remainder: Long): Map<PersonId, Long> {
        if (remainder == 0L) return raw
        val sortedKeys = raw.keys.sortedBy { it.value }
        val mutable = raw.toMutableMap()
        if (remainder > 0) {
            var left = remainder
            for (k in sortedKeys) {
                if (left == 0L) break
                mutable[k] = (mutable[k] ?: 0L) + 1
                left--
            }
        } else {
            var left = -remainder
            for (k in sortedKeys) {
                if (left == 0L) break
                mutable[k] = (mutable[k] ?: 0L) - 1
                left--
            }
        }
        return mutable
    }
}
