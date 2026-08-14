package app.splitup.ui.screens.groups

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.BalanceText
import app.splitup.ui.components.ExpenseRow
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.MonthHeader
import app.splitup.ui.util.monthHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

class NonGroupViewModel(
    expenses: ExpenseRepository,
    people: PersonRepository,
) : ViewModel() {
    data class State(
        val expenses: List<Expense> = emptyList(),
        val nameById: Map<PersonId, String> = emptyMap(),
        val me: PersonId? = null,
        val myNet: Map<Currency, Money> = emptyMap(),
    )

    val state: StateFlow<State> = combine(expenses.observeNonGroup(), people.observeAll()) { exps, ppl ->
        val me = ppl.firstOrNull { it.isMe }?.id
        State(
            expenses = exps,
            nameById = ppl.associate { it.id to it.displayName },
            me = me,
            myNet = me?.let { DebtSimplifier.netOf(exps, it) }.orEmpty(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())
}

/** Splitwise's "Non-group expenses" pseudo-group: everything outside a group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NonGroupScreen(
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onSettleUp: () -> Unit,
    onOpenExpense: (ExpenseId) -> Unit,
) {
    val vm: NonGroupViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val me = state.me

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Non-group expenses", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                icon = { Icon(Icons.Outlined.ReceiptLong, contentDescription = null) },
                text = { Text("Add expense") },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "header") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (state.myNet.isEmpty()) {
                        Text(
                            "You are all settled up",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.myNet.values.forEach { net ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (net.isPositive) "You are owed " else "You owe ",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                BalanceText(amount = net, bold = true)
                            }
                        }
                        OutlinedButton(onClick = onSettleUp, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Settle up")
                        }
                    }
                }
            }
            val grouped = state.expenses.groupBy { it.date.monthHeader() }
            grouped.forEach { (month, monthExpenses) ->
                item(key = "month-$month") {
                    MonthHeader(month)
                }
                items(monthExpenses, key = { it.id.value }) { e ->
                    if (me != null) {
                        ExpenseRow(expense = e, me = me, nameById = state.nameById, onClick = { onOpenExpense(e.id) })
                        ListDivider()
                    }
                }
            }
            item(key = "footer") { Spacer(Modifier.height(96.dp)) }
        }
    }
}
