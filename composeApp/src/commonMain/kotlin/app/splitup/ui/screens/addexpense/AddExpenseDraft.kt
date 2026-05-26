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

    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setAmount(v: String) {
        _state.update { s ->
            val filtered = v.filterIndexed { i, c -> c.isDigit() || (c == '.' && v.indexOf('.') == i) }
            val updated = s.copy(amount = filtered)
            // keep payer totals in sync when the user is single-payer (most common case)
            val total = runCatching { if (filtered.isBlank()) Money.zero(s.currency) else Money.parse(filtered, s.currency) }
                .getOrDefault(Money.zero(s.currency))
            if (updated.payers.size == 1) {
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
            s.copy(payers = mapOf(person to total))
        }
    }
    fun setStrategy(mode: SplitMode) = _state.update { it.copy(strategy = mode) }
    fun toggleParticipant(person: PersonId) {
        _state.update { s ->
            val next = if (person in s.participants) s.participants - person else s.participants + person
            s.copy(participants = next)
        }
    }

    fun buildStrategy(): SplitStrategy = when (state.value.strategy) {
        SplitMode.Equal -> SplitStrategy.Equal(state.value.participants)
        SplitMode.Unequally -> {
            // Equal fallback when "unequally" picked but no per-person input yet.
            SplitStrategy.Equal(state.value.participants)
        }
        SplitMode.Percent -> {
            val n = state.value.participants.size
            require(n > 0)
            val share = 10_000 / n
            val remainder = 10_000 - share * n
            SplitStrategy.Percent(
                state.value.participants.mapIndexed { i, p -> p to share + if (i < remainder) 1 else 0 }.toMap(),
            )
        }
        SplitMode.Shares -> SplitStrategy.Shares(state.value.participants.associateWith { 1 })
    }
}
