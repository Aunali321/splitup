package app.splitup.ui.screens.addexpense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.data.repository.UserPreferencesRepository
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.usecase.AddExpenseUseCase
import app.splitup.shared.domain.split.SplitStrategy
import app.splitup.ui.components.AmountInput
import app.splitup.ui.components.CurrencyPicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

class AddExpenseViewModel(
    private val addExpense: AddExpenseUseCase,
    prefs: UserPreferencesRepository,
    people: PersonRepository,
) : ViewModel() {

    data class State(
        val currency: Currency = Currency.DEFAULT,
        val people: List<app.splitup.shared.domain.model.Person> = emptyList(),
        val me: PersonId? = null,
    )

    val state: StateFlow<State> = combine(prefs.observe(), people.observeAll()) { p, ppl ->
        State(
            currency = p?.homeCurrency ?: Currency.DEFAULT,
            people = ppl,
            me = ppl.firstOrNull { it.isMe }?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private val _selectedCurrency = MutableStateFlow<Currency?>(null)
    val selectedCurrency: StateFlow<Currency?> = _selectedCurrency

    fun setCurrency(c: Currency) { _selectedCurrency.value = c }

    fun submit(
        groupId: GroupId?,
        description: String,
        amount: String,
        currency: Currency,
        participantIds: List<PersonId>,
        payer: PersonId,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val total = Money.parse(amount, currency)
            addExpense(
                AddExpenseUseCase.Input(
                    groupId = groupId,
                    description = description,
                    total = total,
                    date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    createdBy = payer,
                    payers = mapOf(payer to total),
                    strategy = SplitStrategy.Equal(participantIds),
                )
            )
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String?,
    friendId: String?,
    onDone: () -> Unit,
) {
    val vm: AddExpenseViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val selectedOverride by vm.selectedCurrency.collectAsStateWithLifecycle()
    val currency = selectedOverride ?: state.currency
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var showCurrencyPicker by remember { mutableStateOf(false) }

    if (showCurrencyPicker) {
        CurrencyPicker(
            onPick = {
                vm.setCurrency(it)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add expense", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, contentDescription = "Cancel") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            AmountInput(
                currency = currency,
                value = amount,
                onValueChange = { amount = it },
                onCurrencyClick = { showCurrencyPicker = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Paid by you") })
                AssistChip(onClick = {}, label = { Text("Split equally") })
            }
            Spacer(Modifier.height(8.dp))
            val me = state.me
            Button(
                onClick = {
                    if (me != null && description.isNotBlank() && amount.isNotBlank()) {
                        vm.submit(
                            groupId = groupId?.let { GroupId(it) },
                            description = description,
                            amount = amount,
                            currency = currency,
                            participantIds = state.people.map { it.id },
                            payer = me,
                            onDone = onDone,
                        )
                    }
                },
                enabled = me != null && description.isNotBlank() && amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}
