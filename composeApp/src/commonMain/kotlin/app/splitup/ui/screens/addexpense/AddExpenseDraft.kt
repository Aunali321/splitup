package app.splitup.ui.screens.addexpense

import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.DefaultCategories
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.split.SplitStrategy
import app.splitup.ui.util.cleanDecimal
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
        val categoryId: CategoryId = DefaultCategories.UNCATEGORIZED,
        val notes: String = "",
        val repeat: RepeatInterval = RepeatInterval.NEVER,
        /** `file://` URI of an attached receipt, or a remote URL when editing an import. */
        val receiptUrl: String? = null,
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

    enum class SplitMode { Equal, Unequally, Percent, Shares, Adjustment }

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
            val filtered = cleanDecimal(v, s.currency.decimals)
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
    fun setCurrency(c: Currency) = _state.update { s ->
        // payers store Money, so re-denominate them in the new currency — otherwise
        // payerError()'s Money math would compare mismatched currencies and throw.
        val payers = if (s.multiplePayers) {
            parsePayers(s.payerInputs, c)
        } else {
            val total = runCatching { Money.parse(s.amount, c) }.getOrDefault(Money.zero(c))
            s.payers.keys.firstOrNull()?.let { mapOf(it to total) }.orEmpty()
        }
        s.copy(currency = c, payers = payers)
    }
    fun setDate(d: LocalDate) = _state.update { it.copy(date = d) }
    fun setCategory(c: CategoryId) = _state.update { it.copy(categoryId = c) }
    fun setNotes(v: String) = _state.update { it.copy(notes = v) }
    fun setRepeat(r: RepeatInterval) = _state.update { it.copy(repeat = r) }
    fun setReceipt(url: String?) = _state.update { it.copy(receiptUrl = url) }
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
                // Percent precision is basis points (2 decimals), independent of the
                // money currency — a ¥ expense can still split 33.33/33.33/33.34.
                SplitMode.Percent -> cleanDecimal(value, PERCENT_DECIMALS)
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

    fun setParticipants(people: List<PersonId>) = _state.update { it.copy(participants = people) }

    /** Sum of the amounts entered so far (Unequally/Adjustment modes). */
    fun enteredTotal(): Money = state.value.let { s ->
        s.participants.fold(Money.zero(s.currency)) { acc, id -> acc + exactAmount(s, id) }
    }

    fun enteredBasisPoints(): Int = state.value.let { s -> s.participants.sumOf { basisPoints(s, it) } }

    fun enteredShares(): Int = state.value.let { s -> s.participants.sumOf { shareCount(s, it) } }

    /** What [person] would owe under the current inputs — the per-row preview. */
    fun owedPreview(person: PersonId): Money {
        val s = state.value
        val zero = Money.zero(s.currency)
        if (person !in s.participants) return zero
        val total = runCatching { Money.parse(s.amount, s.currency) }.getOrNull() ?: return zero
        return when (s.strategy) {
            SplitMode.Equal -> Money.ofMinor(total.minorUnits / s.participants.size, s.currency)
            SplitMode.Unequally -> exactAmount(s, person)
            SplitMode.Percent ->
                Money.ofMinor(total.minorUnits * basisPoints(s, person) / TOTAL_BP, s.currency)
            SplitMode.Shares -> {
                val totalShares = s.participants.sumOf { shareCount(s, it) }
                if (totalShares == 0) zero
                else Money.ofMinor(total.minorUnits * shareCount(s, person) / totalShares, s.currency)
            }
            SplitMode.Adjustment -> {
                val sumAdj = s.participants.fold(zero) { acc, id -> acc + exactAmount(s, id) }
                val remainder = (total.minorUnits - sumAdj.minorUnits).coerceAtLeast(0)
                Money.ofMinor(remainder / s.participants.size, s.currency) + exactAmount(s, person)
            }
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
            SplitMode.Adjustment -> SplitStrategy.Adjustment(
                participants = parts,
                adjustments = parts.associateWith { exactAmount(s, it) }.filterValues { it.isPositive },
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
            SplitMode.Adjustment -> {
                val sum = s.participants.fold(Money.zero(s.currency)) { acc, id -> acc + exactAmount(s, id) }
                // The engine splits the remainder equally, so adjustments must leave some of the total.
                if (sum < total) null
                else "Adjustments can't reach the total (${(sum - total).abs().format()} over)"
            }
        }
    }

    private fun exactAmount(s: State, id: PersonId): Money =
        runCatching { Money.parse(s.splitInputs[id]?.ifBlank { "0" } ?: "0", s.currency) }
            .getOrDefault(Money.zero(s.currency))

    private fun basisPoints(s: State, id: PersonId): Int = parsePercent(s.splitInputs[id])

    private fun shareCount(s: State, id: PersonId): Int = s.splitInputs[id]?.trim()?.toIntOrNull() ?: 0

    companion object {
        const val TOTAL_BP = 10_000
        private const val PERCENT_DECIMALS = 2

        /** "33.33" → 3333 basis points; blank or malformed → 0. */
        fun parsePercent(raw: String?): Int {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isBlank()) return 0
            val parts = trimmed.split('.')
            val whole = parts[0].toIntOrNull() ?: 0
            val frac = parts.getOrNull(1)?.take(2)?.padEnd(2, '0')?.toIntOrNull() ?: 0
            return whole * 100 + frac
        }

        private fun parsePayers(inputs: Map<PersonId, String>, currency: Currency): Map<PersonId, Money> =
            inputs.filterValues { it.isNotBlank() }
                .mapNotNull { (id, raw) -> runCatching { id to Money.parse(raw, currency) }.getOrNull() }
                .toMap()

        fun formatPercent(basisPoints: Int): String {
            val whole = basisPoints / 100
            val frac = basisPoints % 100
            return if (frac == 0) "$whole" else "$whole.${frac.toString().padStart(2, '0')}"
        }
    }
}
