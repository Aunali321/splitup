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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.debt.Debt
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.SettlementMethod
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.usecase.SettleUpUseCase
import app.splitup.ui.components.BalanceText
import app.splitup.ui.components.PersonAvatar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class SettleUpViewModel(
    private val groupId: GroupId?,
    private val friendId: PersonId?,
    private val settleUseCase: SettleUpUseCase,
    expenses: ExpenseRepository,
    private val people: PersonRepository,
) : ViewModel() {

    data class State(
        val debts: List<Debt> = emptyList(),
        val nameById: Map<PersonId, String> = emptyMap(),
    )

    private val expensesFlow = when {
        groupId != null -> expenses.observeInGroup(groupId)
        friendId != null -> expenses.observeWithFriend(friendId)
        else -> expenses.observeRecent(500)
    }

    val state: StateFlow<State> = combine(expensesFlow, people.observeAll()) { exps, ppl ->
        State(
            debts = settleUseCase.plan(exps),
            nameById = ppl.associate { it.id to it.displayName },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun record(debt: Debt, onDone: () -> Unit) {
        viewModelScope.launch {
            _saving.value = true
            try {
                settleUseCase.record(
                    groupId = groupId,
                    from = debt.from,
                    to = debt.to,
                    amount = debt.amount,
                    method = SettlementMethod.UNSPECIFIED,
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
                            "These are the minimum transfers that clear all balances. Tap one to record it as paid.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.debts, key = { it.from.value + "->" + it.to.value + it.amount.minorUnits }) { debt ->
                        DebtCard(
                            debt = debt,
                            fromName = state.nameById[debt.from] ?: debt.from.value,
                            toName = state.nameById[debt.to] ?: debt.to.value,
                            saving = saving,
                            onConfirm = { vm.record(debt, onDone) },
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
                    Text("$fromName pays $toName", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    BalanceText(amount = debt.amount, bold = true)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onConfirm, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "Saving…" else "Mark as paid")
            }
        }
    }
}
