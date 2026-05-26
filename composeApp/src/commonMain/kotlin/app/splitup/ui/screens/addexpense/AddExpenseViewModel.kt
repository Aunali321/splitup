package app.splitup.ui.screens.addexpense

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitup.shared.data.repository.UserPreferencesRepository
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.usecase.AddExpenseUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * App-lifetime ViewModel shared by the Add Expense form and the two pickers
 * (Paid-by, Split). Lifecycle is managed via [start] / [reset] from the form
 * — pickers are pure consumers of the same draft.
 */
class AddExpenseViewModel(
    private val addExpense: AddExpenseUseCase,
    prefs: UserPreferencesRepository,
    private val people: PersonRepository,
    private val groups: GroupRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    data class Scope(
        val scopeLabel: String,
        val groupId: GroupId? = null,
        val friendId: PersonId? = null,
        val members: List<Person> = emptyList(),
        val leadingIcon: @Composable (() -> Unit)? = null,
    )

    private val _scope = MutableStateFlow(Scope("everyone"))
    val scope: StateFlow<Scope> = _scope

    private val _draftReady = MutableStateFlow(false)
    val draftReady: StateFlow<Boolean> = _draftReady
    private lateinit var draftBacking: AddExpenseDraft
    val draft: AddExpenseDraft get() = draftBacking

    private val homePrefs: StateFlow<Currency> =
        combine(prefs.observe(), people.observeAll()) { p, _ -> p.homeCurrency }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Currency.DEFAULT)

    /**
     * Open a fresh draft for a given scope. Idempotent within the same scope —
     * calling twice from a recomposition won't wipe user input.
     */
    fun start(groupId: GroupId?, friendId: PersonId?) {
        // If we're re-opening for the same scope, keep existing draft.
        val existing = _scope.value
        if (_draftReady.value && existing.groupId == groupId && existing.friendId == friendId) return

        viewModelScope.launch {
            val me = people.getMe()
            val currency = homePrefs.value
            val today = clock.now().toLocalDateTime(timeZone).date

            val (label, members) = when {
                groupId != null -> {
                    val g = groups.get(groupId) ?: return@launch
                    val memberPeople = g.members.mapNotNull { people.get(it.personId) }
                    "All of ${g.name}" to memberPeople
                }
                friendId != null -> {
                    val friend = people.get(friendId) ?: return@launch
                    friend.displayName to listOfNotNull(me, friend)
                }
                else -> {
                    val all: List<Person> = people.observeFriends().first()
                    "everyone" to listOfNotNull(me) + all
                }
            }

            _scope.value = Scope(
                scopeLabel = label,
                groupId = groupId,
                friendId = friendId,
                members = members,
            )
            draftBacking = AddExpenseDraft(
                initialCurrency = currency,
                initialMe = me?.id,
                initialParticipants = members.map { it.id },
                initialDate = today,
            )
            _draftReady.value = true
        }
    }

    fun reset() {
        _draftReady.value = false
        _scope.value = Scope("everyone")
    }

    fun payerLabel(scope: Scope, state: AddExpenseDraft.State): String {
        val payers = state.payers.keys
        return when {
            payers.isEmpty() -> "—"
            payers.size > 1 -> "${payers.size} people"
            else -> {
                val pid = payers.first()
                val person = scope.members.firstOrNull { it.id == pid }
                if (person?.isMe == true) "you" else (person?.displayName ?: "someone")
            }
        }
    }

    fun submit(
        groupId: GroupId?,
        friendId: PersonId?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val s = draft.state.value
            val total = Money.parse(s.amount.ifBlank { "0" }, s.currency)
            require(total.isPositive) { "Amount must be positive" }
            val payers = if (s.payers.size == 1) mapOf(s.payers.keys.first() to total) else s.payers
            addExpense(
                AddExpenseUseCase.Input(
                    groupId = groupId,
                    description = s.description,
                    total = total,
                    date = s.date,
                    createdBy = payers.keys.first(),
                    payers = payers,
                    strategy = draft.buildStrategy(),
                ),
            )
            reset()
            onDone()
        }
    }
}
