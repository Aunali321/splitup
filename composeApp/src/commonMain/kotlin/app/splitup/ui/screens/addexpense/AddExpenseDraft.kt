package app.splitup.ui.screens.addexpense

import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.split.SplitStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * Cross-screen draft for the Add Expense flow. The main form sets description,
 * amount, payers, and the participant set; the PaidBy and Split pickers mutate
 * the same instance and pop back. AddExpenseViewModel hands this to whichever
 * screen is currently composed.
 *
 * A single shared draft beats argument-passing through nav routes because the
 * payers map and the strategy carry too much state to fit in a serialisable arg.
 */
class AddExpenseDraft(
    initialCurrency: Currency,
    initialMe: PersonId?,
    initialParticipants: List<PersonId>,
    initialDate: LocalDate,
) {
    data class State(
        val description: String = "",
        val amount: String = "",
        val currency: Currency,
        val date: LocalDate,
        val participants: List<PersonId>,
        val payers: Map<PersonId, Money>,
        val strategy: SplitMode = SplitMode.Equal,
        // Per-person raw text for the non-equal modes. Interpreted against [strategy]:
        // an amount for Unequally, a percentage for Percent, a share count for Shares.
        val splitInputs: Map<PersonId, String> = emptyMap(),
        // When true, [payers] is driven by per-payer amounts ([payerInputs]) that must
        // sum to the total; otherwise a single payer covers the whole amount.
        val multiplePayers: Boolean = false,
        val payerInputs: Map<PersonId, String> = emptyMap(),
    )

    enum class SplitMode { Equal, Unequally, Percent, Shares }

    private val _state = MutableStateFlow(
        State(
            currency = initialCurrency,
            date = initialDate,
            participants = initialParticipants,
            payers = initialMe?.let { mapOf(it to Money.zero(initialCurrency)) }.orEmpty(),
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    /** Replace the whole draft at once — used to hydrate the form when editing an expense. */
    fun set(newState: State) { _state.value = newState }

    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setAmount(v: String) {
        _state.update { s ->
            val filtered = v.filterIndexed { i, c -> c.isDigit() || (c == '.' && v.indexOf('.') == i) }
            val updated = s.copy(amount = filtered)
            // keep the payer total in sync for the single-payer case (most common);
            // in multi-payer mode the user owns the per-payer amounts, so leave them be.
            val total = runCatching { if (filtered.isBlank()) Money.zero(s.currency) else Money.parse(filtered, s.currency) }
                .getOrDefault(Money.zero(s.currency))
            if (!s.multiplePayers && updated.payers.size == 1) {
                val payer = updated.payers.keys.first()
                updated.copy(payers = mapOf(payer to total))
            } else updated
        }
    }
    fun setCurrency(c: Currency) = _state.update { it.copy(currency = c) }
    fun setDate(d: LocalDate) = _state.update { it.copy(date = d) }
    fun setSinglePayer(person: PersonId) {
        _state.update { s ->
            val total = runCatching { if (s.amount.isBlank()) Money.zero(s.currency) else Money.parse(s.amount, s.currency) }
                .getOrDefault(Money.zero(s.currency))
            s.copy(multiplePayers = false, payerInputs = emptyMap(), payers = mapOf(person to total))
        }
    }

    fun setMultiplePayers(enabled: Boolean) = _state.update { s ->
        if (s.multiplePayers == enabled) s
        else if (enabled) {
            // Seed the per-payer fields from the current single payer so the inputs match
            // the amount already shown in the footer (Splitwise pre-fills the payer too).
            s.copy(multiplePayers = true, payerInputs = s.payers.mapValues { it.value.toPlainString() })
        } else {
            // Collapse back to a single payer: keep whoever's first, covering the full total.
            val total = runCatching { Money.parse(s.amount, s.currency) }.getOrDefault(Money.zero(s.currency))
            val first = s.payers.keys.firstOrNull()
            s.copy(multiplePayers = false, payerInputs = emptyMap(), payers = first?.let { mapOf(it to total) }.orEmpty())
        }
    }

    fun setPayerInput(person: PersonId, value: String) = _state.update { s ->
        val inputs = s.payerInputs + (person to cleanDecimal(value, s.currency.decimals))
        s.copy(payerInputs = inputs, payers = parsePayers(inputs, s.currency))
    }

    /** Null when the chosen payer(s) are valid for the current total, else a short reason. */
    fun payerError(): String? {
        val s = state.value
        if (!s.multiplePayers) return if (s.payers.isEmpty()) "Select who paid" else null
        if (s.payers.isEmpty()) return "Enter who paid what"
        val total = runCatching { Money.parse(s.amount, s.currency) }.getOrNull() ?: return "Enter an amount"
        val sum = s.payers.values.fold(Money.zero(s.currency)) { acc, m -> acc + m }
        return if (sum == total) null
        else "${(total - sum).abs().format()} ${if (sum < total) "left" else "over"}"
    }
    // Switching modes clears the per-person inputs — a percentage and an amount
    // can't be reinterpreted across modes, so a fresh slate avoids stale values.
    fun setStrategy(mode: SplitMode) = _state.update {
        if (it.strategy == mode) it else it.copy(strategy = mode, splitInputs = emptyMap())
    }

    fun setSplitInput(person: PersonId, value: String) {
        _state.update { s ->
            val cleaned = when (s.strategy) {
                SplitMode.Shares -> value.filter { it.isDigit() }
                else -> cleanDecimal(value, s.currency.decimals)
            }
            s.copy(splitInputs = s.splitInputs + (person to cleaned))
        }
    }

    fun toggleParticipant(person: PersonId) {
        _state.update { s ->
            val next = if (person in s.participants) s.participants - person else s.participants + person
            s.copy(participants = next)
        }
    }

    fun buildStrategy(): SplitStrategy {
        val s = state.value
        val parts = s.participants
        return when (s.strategy) {
            SplitMode.Equal -> SplitStrategy.Equal(parts)
            SplitMode.Unequally -> SplitStrategy.Exact(parts.associateWith { exactAmount(s, it) })
            SplitMode.Percent -> SplitStrategy.Percent(parts.associateWith { basisPoints(s, it) })
            SplitMode.Shares -> SplitStrategy.Shares(
                parts.associateWith { shareCount(s, it) }.filterValues { it > 0 },
            )
        }
    }

    /**
     * Null when the current split can be saved, otherwise a short reason fit for a
     * footer hint. Guards against the split engine's hard requirements (Exact must
     * sum to the total, Percent to 100%, Shares need a positive count) so we never
     * submit an allocation that would throw.
     */
    fun splitError(): String? {
        val s = state.value
        if (s.participants.isEmpty()) return "Select at least one person"
        val total = runCatching { Money.parse(s.amount, s.currency) }.getOrNull()
            ?: return "Enter an amount"
        if (!total.isPositive) return "Enter an amount"
        return when (s.strategy) {
            SplitMode.Equal -> null
            SplitMode.Unequally -> {
                val sum = s.participants.fold(Money.zero(s.currency)) { acc, id -> acc + exactAmount(s, id) }
                if (sum == total) null
                else "${(total - sum).abs().format()} ${if (sum < total) "left" else "over"}"
            }
            SplitMode.Percent -> {
                val sum = s.participants.sumOf { basisPoints(s, it) }
                if (sum == TOTAL_BP) null
                else "${formatPercent(if (sum < TOTAL_BP) TOTAL_BP - sum else sum - TOTAL_BP)}% ${if (sum < TOTAL_BP) "left" else "over"}"
            }
            SplitMode.Shares ->
                if (s.participants.any { shareCount(s, it) > 0 }) null else "Add at least one share"
        }
    }

    /** A short "entered of expected" line for the current mode, e.g. "₹40.00 of ₹100.00". */
    fun splitSummary(): String {
        val s = state.value
        val total = runCatching { Money.parse(s.amount, s.currency) }.getOrNull() ?: Money.zero(s.currency)
        return when (s.strategy) {
            SplitMode.Equal -> {
                val n = s.participants.size
                "$n ${if (n == 1) "person" else "people"}"
            }
            SplitMode.Unequally -> {
                val sum = s.participants.fold(Money.zero(s.currency)) { acc, id -> acc + exactAmount(s, id) }
                "${sum.format()} of ${total.format()}"
            }
            SplitMode.Percent -> {
                val sum = s.participants.sumOf { basisPoints(s, it) }
                "${formatPercent(sum)}% of 100%"
            }
            SplitMode.Shares -> {
                val sum = s.participants.sumOf { shareCount(s, it) }
                "$sum ${if (sum == 1) "share" else "shares"}"
            }
        }
    }

    private fun exactAmount(s: State, id: PersonId): Money =
        runCatching { Money.parse(s.splitInputs[id]?.ifBlank { "0" } ?: "0", s.currency) }
            .getOrDefault(Money.zero(s.currency))

    private fun basisPoints(s: State, id: PersonId): Int {
        val raw = s.splitInputs[id]?.trim().orEmpty()
        if (raw.isBlank()) return 0
        val parts = raw.split('.')
        val whole = parts[0].toIntOrNull() ?: 0
        val frac = parts.getOrNull(1)?.take(2)?.padEnd(2, '0')?.toIntOrNull() ?: 0
        return whole * 100 + frac
    }

    private fun shareCount(s: State, id: PersonId): Int = s.splitInputs[id]?.trim()?.toIntOrNull() ?: 0

    companion object {
        private const val TOTAL_BP = 10_000

        private fun parsePayers(inputs: Map<PersonId, String>, currency: Currency): Map<PersonId, Money> =
            inputs.filterValues { it.isNotBlank() }
                .mapNotNull { (id, raw) -> runCatching { id to Money.parse(raw, currency) }.getOrNull() }
                .toMap()

        private fun cleanDecimal(value: String, decimals: Int): String {
            val dot = value.indexOf('.')
            val filtered = value.filterIndexed { i, c -> c.isDigit() || (c == '.' && i == dot) }
            if (decimals == 0) return filtered.substringBefore('.')
            val sep = filtered.indexOf('.')
            return if (sep < 0) filtered else filtered.substring(0, minOf(filtered.length, sep + 1 + decimals))
        }

        fun formatPercent(basisPoints: Int): String {
            val whole = basisPoints / 100
            val frac = basisPoints % 100
            return if (frac == 0) "$whole" else "$whole.${frac.toString().padStart(2, '0')}"
        }
    }
}
