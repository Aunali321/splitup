package app.splitup.ui.screens.addexpense

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * Single ViewModel shared by the main Add Expense form and the Paid-by / Split
 * pickers — they all need the same draft.
 *
 * Lifecycle: created when the user opens Add Expense, retained across the picker
 * sub-routes via the nav graph's lifecycle scope. (Koin's default scope is fine —
 * each call to koinViewModel() inside the same NavBackStackEntry shares it.)
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

    val homePrefs: StateFlow<Currency> = combine(prefs.observe(), people.observeAll()) { p, _ -> p.homeCurrency }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Currency.DEFAULT)

    fun bindScope(groupId: GroupId?, friendId: PersonId?) {
        viewModelScope.launch {
            val me = people.getMe()
            val currency = homePrefs.value
            val today = clock.now().toLocalDateTime(timeZone).date
            when {
                groupId != null -> {
                    val g = groups.get(groupId) ?: return@launch
                    val memberPeople = g.members.mapNotNull { people.get(it.personId) }
                    _scope.value = Scope(
                        scopeLabel = "All of ${g.name}",
                        groupId = groupId,
                        members = memberPeople,
                    )
                    if (!_draftReady.value) {
                        draftBacking = AddExpenseDraft(
                            initialCurrency = currency,
                            initialMe = me?.id,
                            initialParticipants = memberPeople.map { it.id },
                            initialDate = today,
                        )
                        _draftReady.value = true
                    }
                }
                friendId != null -> {
                    val friend = people.get(friendId) ?: return@launch
                    val participants = listOfNotNull(me, friend)
                    _scope.value = Scope(
                        scopeLabel = friend.displayName,
                        friendId = friendId,
                        members = participants,
                    )
                    if (!_draftReady.value) {
                        draftBacking = AddExpenseDraft(
                            initialCurrency = currency,
                            initialMe = me?.id,
                            initialParticipants = participants.map { it.id },
                            initialDate = today,
                        )
                        _draftReady.value = true
                    }
                }
                else -> {
                    val all: List<Person> = people.observeFriends().first()
                    val participants: List<Person> = listOfNotNull(me) + all
                    _scope.value = Scope(scopeLabel = "everyone", members = participants)
                    if (!_draftReady.value) {
                        draftBacking = AddExpenseDraft(
                            initialCurrency = currency,
                            initialMe = me?.id,
                            initialParticipants = participants.map { it.id },
                            initialDate = today,
                        )
                        _draftReady.value = true
                    }
                }
            }
        }
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
            // Re-balance single-payer to the (possibly-changed) total.
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
            onDone()
        }
    }
}
