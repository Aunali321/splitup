package app.splitup.ui.screens.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.debt.Debt
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.BalanceText
import app.splitup.ui.components.ExpenseRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class GroupDetailViewModel(
    groupId: GroupId,
    groups: GroupRepository,
    expenses: ExpenseRepository,
    people: PersonRepository,
) : ViewModel() {

    data class State(
        val group: Group?,
        val expenses: List<Expense>,
        val debts: List<Debt>,
        val nameById: Map<PersonId, String>,
        val me: PersonId?,
    )

    val state: StateFlow<State> = combine(
        groups.observe(groupId),
        expenses.observeInGroup(groupId),
        people.observeAll(),
    ) { g, exps, ppl ->
        State(
            group = g,
            expenses = exps,
            debts = DebtSimplifier.simplify(DebtSimplifier.debtsFromExpenses(exps)),
            nameById = ppl.associate { it.id to it.displayName },
            me = ppl.firstOrNull { it.isMe }?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State(null, emptyList(), emptyList(), emptyMap(), null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onSettleUp: () -> Unit,
    onOpenExpense: (ExpenseId) -> Unit,
) {
    val vm: GroupDetailViewModel = koinViewModel(parameters = { parametersOf(GroupId(groupId)) })
    val state by vm.state.collectAsStateWithLifecycle()
    val me = state.me
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.group?.name.orEmpty(), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add expense") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (state.debts.isEmpty()) {
                            Text("All settled up!", style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text("Balances", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            state.debts.forEach { d ->
                                val fromName = state.nameById[d.from] ?: d.from.value
                                val toName = state.nameById[d.to] ?: d.to.value
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("$fromName  →  $toName", style = MaterialTheme.typography.bodyMedium)
                                    BalanceText(amount = d.amount, bold = true)
                                }
                            }
                            OutlinedButton(onClick = onSettleUp, modifier = Modifier.padding(top = 8.dp)) {
                                Icon(Icons.Outlined.Handshake, contentDescription = null)
                                Text("  Settle up")
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
                items(state.expenses, key = { it.id.value }) { e ->
                    val payer = e.shares.firstOrNull { it.paidShare.isPositive }?.personId
                    val payerName = payer?.let { state.nameById[it] } ?: "Someone"
                    if (me != null) {
                        ExpenseRow(expense = e, me = me, payerName = payerName, onClick = { onOpenExpense(e.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}
