package app.splitup.ui.screens.settleup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.debt.Debt
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.usecase.SettleUpUseCase
import app.splitup.ui.components.BalanceText
import app.splitup.ui.components.PersonAvatar
import app.splitup.ui.components.PickDateDialog
import app.splitup.ui.util.cleanDecimal
import app.splitup.ui.util.formatShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class SettleUpViewModel(
    private val groupId: GroupId?,
    private val friendId: PersonId?,
    private val settleUseCase: SettleUpUseCase,
    expenses: ExpenseRepository,
    groups: GroupRepository,
    private val people: PersonRepository,
) : ViewModel() {

    data class State(
        val debts: List<Debt> = emptyList(),
        val personById: Map<PersonId, Person> = emptyMap(),
        val me: PersonId? = null,
    )

    private val groupFlow = groupId?.let { groups.observe(it) } ?: flowOf(null)

    // Group scope plans within the group; friend scope covers the whole
    // relationship (group and non-group), matching the balance the friend screen
    // shows; no scope means the Non-group screen. Friend and non-group payments
    // are recorded outside any group.
    private val expensesFlow = when {
        groupId != null -> expenses.observeInGroup(groupId)
        else -> expenses.observeAll()
    }

    val state: StateFlow<State> = combine(expensesFlow, groupFlow, people.observeAll()) { exps, group, ppl ->
        val me = ppl.firstOrNull { it.isMe }?.id
        State(
            debts = when {
                groupId != null -> settleUseCase.plan(exps, simplify = group?.simplifyByDefault == true)
                friendId != null -> friendDebts(exps, me)
                else -> settleUseCase.plan(exps.filter { it.groupId == null }, simplify = false)
            },
            personById = ppl.associateBy { it.id },
            me = me,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** One net debt per currency between me and the friend, same math as their header. */
    private fun friendDebts(expenses: List<Expense>, me: PersonId?): List<Debt> {
        if (me == null || friendId == null) return emptyList()
        return DebtSimplifier.balancesToward(expenses, me)[friendId].orEmpty().map { (_, net) ->
            if (net.isPositive) Debt(from = friendId, to = me, amount = net)
            else Debt(from = me, to = friendId, amount = -net)
        }
    }

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun record(
        from: PersonId,
        to: PersonId,
        amount: Money,
        date: LocalDate,
        notes: String?,
        onDone: () -> Unit,
    ) {
        val me = state.value.me ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                settleUseCase.record(
                    groupId = groupId,
                    from = from,
                    to = to,
                    amount = amount,
                    recordedBy = me,
                    notes = notes,
                    date = date,
                )
                onDone()
            } finally {
                _saving.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    groupId: String?,
    friendId: String?,
    onDone: () -> Unit,
) {
    val vm: SettleUpViewModel = koinViewModel(
        parameters = { parametersOf(groupId?.let { GroupId(it) }, friendId?.let { PersonId(it) }) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var recording by remember { mutableStateOf<Debt?>(null) }

    recording?.let { debt ->
        RecordPaymentSheet(
            debt = debt,
            personById = state.personById,
            saving = saving,
            onConfirm = { from, to, amount, date, notes ->
                vm.record(from, to, amount, date, notes) {
                    recording = null
                    onDone()
                }
            },
            onDismiss = { recording = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settle up", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.debts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.padding(16.dp))
                    Text("Everyone is settled up!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "There are no outstanding balances to settle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(
                            "Select a balance to settle. These are the minimum transfers that clear everything.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(
                        state.debts,
                        key = { "${it.from.value}->${it.to.value}:${it.amount.currency.code}:${it.amount.minorUnits}" },
                    ) { debt ->
                        DebtCard(
                            debt = debt,
                            fromName = state.personById[debt.from]?.displayName ?: debt.from.value,
                            toName = state.personById[debt.to]?.displayName ?: debt.to.value,
                            saving = saving,
                            onConfirm = { recording = debt },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtCard(
    debt: Debt,
    fromName: String,
    toName: String,
    saving: Boolean,
    onConfirm: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$fromName owes $toName", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    BalanceText(amount = debt.amount, bold = true)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onConfirm, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text("Settle up")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentSheet(
    debt: Debt,
    personById: Map<PersonId, Person>,
    saving: Boolean,
    onConfirm: (from: PersonId, to: PersonId, amount: Money, date: LocalDate, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val currency = debt.amount.currency
    var swapped by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf(debt.amount.toPlainString()) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var notes by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val from = if (swapped) debt.to else debt.from
    val to = if (swapped) debt.from else debt.to
    val amount = runCatching { Money.parse(amountText, currency) }.getOrNull()
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val effectiveDate = date ?: today

    if (showDatePicker) {
        PickDateDialog(
            initial = effectiveDate,
            onPick = { date = it },
            onDismiss = { showDatePicker = false },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Record a payment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                personById[from]?.let { PersonAvatar(it, size = 56.dp) }
                IconButton(onClick = { swapped = !swapped }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Change payer",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                personById[to]?.let { PersonAvatar(it, size = 56.dp) }
            }
            Text(
                paymentLine(from, to, personById),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = cleanDecimal(it, currency.decimals) },
                prefix = { Text(currency.symbol) },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AssistChip(
                onClick = { showDatePicker = true },
                label = { Text(effectiveDate.formatShort()) },
                leadingIcon = { Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Payment description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { amount?.let { onConfirm(from, to, it, effectiveDate, notes.trim().ifBlank { null }) } },
                enabled = !saving && amount?.isPositive == true,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Saving…" else "Record payment") }

            Text(
                "Recording a payment does not move money.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun paymentLine(from: PersonId, to: PersonId, personById: Map<PersonId, Person>): String {
    val fromName = if (personById[from]?.isMe == true) "You" else personById[from]?.displayName ?: "Someone"
    val toName = if (personById[to]?.isMe == true) "you" else personById[to]?.displayName ?: "someone"
    return "$fromName paid $toName"
}
