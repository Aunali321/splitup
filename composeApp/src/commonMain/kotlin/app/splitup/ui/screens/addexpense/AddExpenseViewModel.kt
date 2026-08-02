package app.splitup.ui.screens.addexpense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitup.shared.data.repository.UserPreferencesRepository
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
import app.splitup.ui.platform.ImagePicker
import app.splitup.ui.platform.isLocalReceipt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Scoped to the Add-Expense navigation graph and shared by the form and the two
 * pickers, which are pure consumers of the same draft. The ViewModel is cleared
 * when the flow pops, so a cancelled draft can never resurface on the next open.
 */
class AddExpenseViewModel(
    private val groupId: GroupId?,
    private val friendIds: List<PersonId>,
    private val expenseId: String?,
    private val addExpense: AddExpenseUseCase,
    private val editExpense: EditExpenseUseCase,
    private val prefs: UserPreferencesRepository,
    private val people: PersonRepository,
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    val imagePicker: ImagePicker,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    data class Scope(
        val scopeLabel: String,
        val members: List<Person> = emptyList(),
    )

    private val _scope = MutableStateFlow(Scope("everyone"))
    val scope: StateFlow<Scope> = _scope

    private val _draftReady = MutableStateFlow(false)
    val draftReady: StateFlow<Boolean> = _draftReady
    private lateinit var draftBacking: AddExpenseDraft
    val draft: AddExpenseDraft get() = draftBacking

    /** True when the group/friends/expense this flow was opened for no longer exist. */
    private val _scopeGone = MutableStateFlow(false)
    val scopeGone: StateFlow<Boolean> = _scopeGone.asStateFlow()

    // The expense being edited, or null when adding. Drives the screen title and the
    // submit path (update-in-place vs. create).
    private val _editing = MutableStateFlow<Expense?>(null)
    val editing: StateFlow<Expense?> = _editing

    private var me: PersonId? = null
    private var submitted = false

    init {
        viewModelScope.launch {
            if (expenseId != null) loadForEdit(ExpenseId(expenseId)) else loadForCreate()
        }
    }

    private suspend fun loadForCreate() {
        val meRow = people.getMe()
        me = meRow?.id
        val currency = prefs.get().homeCurrency
        val today = clock.now().toLocalDateTime(timeZone).date

        val group = groupId?.let { groups.get(it) ?: return markScopeGone() }
        val (label, members) = when {
            group != null -> "All of ${group.name}" to group.members.mapNotNull { people.get(it.personId) }
            friendIds.isNotEmpty() -> {
                val friends = friendIds.mapNotNull { people.get(it) }.ifEmpty { return markScopeGone() }
                friends.joinToString(", ") { it.firstName } to listOfNotNull(meRow) + friends
            }
            else -> "everyone" to listOfNotNull(meRow) + people.observeFriends().first()
        }

        _scope.value = Scope(scopeLabel = label, members = members)
        draftBacking = AddExpenseDraft(
            initialCurrency = currency,
            initialMe = me,
            initialParticipants = members.map { it.id },
            initialDate = today,
        )
        group?.defaultSplit?.let { applyDefaultSplit(it, members.mapTo(mutableSetOf()) { m -> m.id }) }
        _draftReady.value = true
    }

    private fun markScopeGone() {
        _scopeGone.value = true
    }

    /** Pre-selects the group's stored default split; skipped when membership drifted. */
    private fun applyDefaultSplit(strategy: SplitStrategy, memberIds: Set<PersonId>) {
        when (strategy) {
            is SplitStrategy.Equal -> draft.setStrategy(AddExpenseDraft.SplitMode.Equal)
            is SplitStrategy.Percent -> if (strategy.basisPoints.keys.all { it in memberIds }) {
                draft.setStrategy(AddExpenseDraft.SplitMode.Percent)
                strategy.basisPoints.forEach { (id, bp) ->
                    draft.setSplitInput(id, AddExpenseDraft.formatPercent(bp))
                }
            }
            is SplitStrategy.Shares -> if (strategy.shares.keys.all { it in memberIds }) {
                draft.setStrategy(AddExpenseDraft.SplitMode.Shares)
                strategy.shares.forEach { (id, n) -> draft.setSplitInput(id, n.toString()) }
            }
            is SplitStrategy.Exact, is SplitStrategy.Adjustment -> Unit
        }
    }

    private suspend fun loadForEdit(id: ExpenseId) {
        val expense = expenses.get(id) ?: return markScopeGone()
        me = people.getMe()?.id
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
            members = members,
        )
        draftBacking = AddExpenseDraft(
            initialCurrency = expense.cost.currency,
            initialMe = me,
            initialParticipants = participants,
            initialDate = expense.date,
        )
        draftBacking.set(
            AddExpenseDraft.State(
                description = expense.description,
                amount = expense.cost.toPlainString(),
                currency = expense.cost.currency,
                date = expense.date,
                categoryId = expense.categoryId,
                notes = expense.notes.orEmpty(),
                repeat = expense.repeatInterval,
                receiptUrl = expense.receiptUrl,
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

    fun attachReceipt(fromCamera: Boolean) {
        viewModelScope.launch {
            val url = if (fromCamera) imagePicker.takePhoto() else imagePicker.pickFromGallery()
            if (url != null) {
                discardDraftReceipt()
                draft.setReceipt(url)
            }
        }
    }

    fun removeReceipt() {
        discardDraftReceipt()
        draft.setReceipt(null)
    }

    /** Deletes the draft's picked-but-unsaved receipt file, if any. */
    private fun discardDraftReceipt() {
        val current = draft.state.value.receiptUrl ?: return
        if (current.isLocalReceipt() && current != _editing.value?.receiptUrl) {
            imagePicker.discard(current)
        }
    }

    override fun onCleared() {
        if (::draftBacking.isInitialized && !submitted) discardDraftReceipt()
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

    fun submit(onDone: () -> Unit) {
        // UI keeps the Save action disabled while these are non-null; guard anyway so a
        // stale recomposition can never feed the split engine an allocation it rejects.
        if (draft.splitError() != null || draft.payerError() != null) return
        val createdBy = me ?: return
        viewModelScope.launch {
            val s = draft.state.value
            val total = Money.parse(s.amount, s.currency)
            val payers = if (s.payers.size == 1) mapOf(s.payers.keys.first() to total) else s.payers
            val original = _editing.value
            if (original != null) {
                editExpense(
                    EditExpenseUseCase.Input(
                        original = original,
                        description = s.description,
                        total = total,
                        date = s.date,
                        categoryId = s.categoryId,
                        notes = s.notes.trim().ifEmpty { null },
                        payers = payers,
                        strategy = draft.buildStrategy(),
                        repeatInterval = s.repeat,
                        receiptUrl = s.receiptUrl,
                    ),
                )
                original.receiptUrl?.takeIf { it.isLocalReceipt() && it != s.receiptUrl }
                    ?.let(imagePicker::discard)
            } else {
                addExpense(
                    AddExpenseUseCase.Input(
                        groupId = groupId,
                        description = s.description,
                        total = total,
                        date = s.date,
                        categoryId = s.categoryId,
                        notes = s.notes.trim().ifEmpty { null },
                        createdBy = createdBy,
                        payers = payers,
                        strategy = draft.buildStrategy(),
                        repeatInterval = s.repeat,
                        receiptUrl = s.receiptUrl,
                    ),
                )
            }
            submitted = true
            onDone()
        }
    }

    /**
     * Recover the editable form state (mode + per-person inputs + participants) from a
     * stored [SplitStrategy].
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
            is SplitStrategy.Adjustment ->
                Triple(
                    AddExpenseDraft.SplitMode.Adjustment,
                    s.adjustments.mapValues { it.value.toPlainString() },
                    s.participants,
                )
        }
}
