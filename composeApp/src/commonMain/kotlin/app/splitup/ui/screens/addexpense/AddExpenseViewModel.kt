package app.splitup.ui.screens.addexpense

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitup.shared.data.repository.UserPreferencesRepository
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.split.SplitStrategy
import app.splitup.shared.domain.usecase.AddExpenseUseCase
import app.splitup.shared.domain.usecase.EditExpenseUseCase
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
    private val editExpense: EditExpenseUseCase,
    prefs: UserPreferencesRepository,
    private val people: PersonRepository,
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
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

    // The expense being edited, or null when adding. Drives the screen title and the
    // submit path (update-in-place vs. create).
    private val _editing = MutableStateFlow<Expense?>(null)
    val editing: StateFlow<Expense?> = _editing

    private val homePrefs: StateFlow<Currency> =
        combine(prefs.observe(), people.observeAll()) { p, _ -> p.homeCurrency }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Currency.DEFAULT)

    /**
     * Open a draft for a scope, or hydrate one from [expenseId] to edit it. Idempotent
     * within the same scope/expense — calling twice from a recomposition (or from a
     * picker that shares this VM) won't wipe user input.
     */
    fun start(groupId: GroupId?, friendId: PersonId?, expenseId: String? = null) {
        val existing = _scope.value
        if (_draftReady.value &&
            existing.groupId == groupId &&
            existing.friendId == friendId &&
            _editing.value?.id?.value == expenseId
        ) {
            return
        }

        viewModelScope.launch {
            if (expenseId != null) {
                loadForEdit(ExpenseId(expenseId), groupId, friendId)
                return@launch
            }

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

            _editing.value = null
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

    private suspend fun loadForEdit(expenseId: ExpenseId, groupId: GroupId?, friendId: PersonId?) {
        val expense = expenses.get(expenseId) ?: return
        val group = expense.groupId?.let { groups.get(it) }
        val members = if (group != null) {
            group.members.mapNotNull { people.get(it.personId) }
        } else {
            expense.shares.mapNotNull { people.get(it.personId) }
        }

        val (mode, splitInputs, participants) = reverseSplit(expense)
        val payers = expense.shares.filter { it.paidShare.isPositive }.associate { it.personId to it.paidShare }

        _editing.value = expense
        _scope.value = Scope(
            scopeLabel = group?.let { "All of ${it.name}" } ?: "you",
            groupId = groupId,
            friendId = friendId,
            members = members,
        )
        draftBacking = AddExpenseDraft(
            initialCurrency = expense.cost.currency,
            initialMe = people.getMe()?.id,
            initialParticipants = participants,
            initialDate = expense.date,
        )
        draftBacking.set(
            AddExpenseDraft.State(
                description = expense.description,
                amount = expense.cost.toPlainString(),
                currency = expense.cost.currency,
                date = expense.date,
                participants = participants,
                payers = payers,
                strategy = mode,
                splitInputs = splitInputs,
                multiplePayers = payers.size > 1,
                payerInputs = if (payers.size > 1) payers.mapValues { it.value.toPlainString() } else emptyMap(),
            ),
        )
        _draftReady.value = true
    }

    fun reset() {
        _draftReady.value = false
        _editing.value = null
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
        // UI keeps the Save action disabled while these are non-null; guard anyway so a
        // stale recomposition can never feed the split engine an allocation it rejects.
        if (draft.splitError() != null || draft.payerError() != null) return
        viewModelScope.launch {
            val s = draft.state.value
            val total = Money.parse(s.amount.ifBlank { "0" }, s.currency)
            require(total.isPositive) { "Amount must be positive" }
            val payers = if (s.payers.size == 1) mapOf(s.payers.keys.first() to total) else s.payers
            val original = _editing.value
            if (original != null) {
                editExpense(
                    EditExpenseUseCase.Input(
                        original = original,
                        description = s.description,
                        total = total,
                        date = s.date,
                        payers = payers,
                        strategy = draft.buildStrategy(),
                    ),
                )
            } else {
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
            }
            reset()
            onDone()
        }
    }

    /**
     * Recover the editable form state (mode + per-person inputs + participants) from a
     * stored [SplitStrategy]. Adjustment has no editor of its own, so it round-trips
     * through the already-computed owed shares as an exact split — numerically faithful.
     */
    private fun reverseSplit(expense: Expense): Triple<AddExpenseDraft.SplitMode, Map<PersonId, String>, List<PersonId>> =
        when (val s = expense.splitStrategy) {
            is SplitStrategy.Equal ->
                Triple(AddExpenseDraft.SplitMode.Equal, emptyMap(), s.participants)
            is SplitStrategy.Exact ->
                Triple(AddExpenseDraft.SplitMode.Unequally, s.amounts.mapValues { it.value.toPlainString() }, s.amounts.keys.toList())
            is SplitStrategy.Percent ->
                Triple(AddExpenseDraft.SplitMode.Percent, s.basisPoints.mapValues { AddExpenseDraft.formatPercent(it.value) }, s.basisPoints.keys.toList())
            is SplitStrategy.Shares ->
                Triple(AddExpenseDraft.SplitMode.Shares, s.shares.mapValues { it.value.toString() }, s.shares.keys.toList())
            is SplitStrategy.Adjustment -> {
                val owed = expense.shares.filter { it.owedShare.isPositive }
                Triple(
                    AddExpenseDraft.SplitMode.Unequally,
                    owed.associate { it.personId to it.owedShare.toPlainString() },
                    owed.map { it.personId },
                )
            }
        }

}
