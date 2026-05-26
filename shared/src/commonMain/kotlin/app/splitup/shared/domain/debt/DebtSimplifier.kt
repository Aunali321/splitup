package app.splitup.shared.domain.debt

import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId

/**
 * Reduces pairwise debts to the minimum-cardinality set of transfers that
 * preserves every participant's net balance. Greedy match of largest creditor
 * with largest debtor — produces ≤ n-1 transfers. Per-currency; never nets
 * across currencies.
 */
object DebtSimplifier {

    fun debtsFromExpenses(expenses: List<Expense>): List<Debt> {
        val byCurrency: MutableMap<Currency, MutableMap<Pair<PersonId, PersonId>, Long>> = HashMap()
        for (e in expenses) {
            if (e.isDeleted) continue
            val bucket = byCurrency.getOrPut(e.currency) { HashMap() }
            val creditors = e.shares.filter { it.paidShare > it.owedShare }
            val debtors = e.shares.filter { it.paidShare < it.owedShare }
            if (creditors.isEmpty() || debtors.isEmpty()) continue

            val creditorBalances = creditors.associate { it.personId to (it.paidShare - it.owedShare).minorUnits }
                .toMutableMap()
            val debtorBalances = debtors.associate { it.personId to (it.owedShare - it.paidShare).minorUnits }

            for ((debtor, owed) in debtorBalances) {
                var remaining = owed
                for ((creditor, available) in creditorBalances.toList()) {
                    if (remaining == 0L) break
                    if (available == 0L) continue
                    val transfer = minOf(remaining, available)
                    val key = debtor to creditor
                    bucket[key] = (bucket[key] ?: 0L) + transfer
                    creditorBalances[creditor] = available - transfer
                    remaining -= transfer
                }
            }
        }
        return byCurrency.flatMap { (currency, pairMap) ->
            pairMap.mapNotNull { (pair, amount) ->
                if (amount <= 0L) null else Debt(pair.first, pair.second, Money.ofMinor(amount, currency))
            }
        }
    }

    fun simplify(debts: List<Debt>): List<Debt> =
        debts.groupBy { it.amount.currency }
            .flatMap { (currency, sameCurrency) -> simplifySingleCurrency(sameCurrency, currency) }

    private fun simplifySingleCurrency(debts: List<Debt>, currency: Currency): List<Debt> {
        val net: MutableMap<PersonId, Long> = HashMap()
        for (d in debts) {
            net[d.from] = (net[d.from] ?: 0L) - d.amount.minorUnits
            net[d.to] = (net[d.to] ?: 0L) + d.amount.minorUnits
        }
        net.values.removeAll { it == 0L }

        val creditors = net.filter { it.value > 0 }
            .toList()
            .sortedWith(compareByDescending<Pair<PersonId, Long>> { it.second }.thenBy { it.first.value })
            .toMutableList()
        val debtors = net.filter { it.value < 0 }
            .toList()
            .sortedWith(compareBy<Pair<PersonId, Long>> { it.second }.thenBy { it.first.value })
            .toMutableList()

        val out = ArrayList<Debt>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val (creditor, credit) = creditors.removeAt(0)
            val (debtor, debit) = debtors.removeAt(0)
            val transfer = minOf(credit, -debit)
            out.add(Debt(debtor, creditor, Money.ofMinor(transfer, currency)))
            val creditorLeft = credit - transfer
            val debtorLeft = debit + transfer
            if (creditorLeft > 0) creditors.add(0, creditor to creditorLeft)
            if (debtorLeft < 0) debtors.add(0, debtor to debtorLeft)
        }
        return out
    }

    fun balances(expenses: List<Expense>): Map<Currency, Map<PersonId, Money>> {
        val out: MutableMap<Currency, MutableMap<PersonId, Long>> = HashMap()
        for (e in expenses) {
            if (e.isDeleted) continue
            val bucket = out.getOrPut(e.currency) { HashMap() }
            for (s in e.shares) {
                bucket[s.personId] = (bucket[s.personId] ?: 0L) + (s.paidShare - s.owedShare).minorUnits
            }
        }
        return out.mapValues { (currency, m) -> m.mapValues { Money.ofMinor(it.value, currency) } }
    }
}
