package app.splitup.ui.screens.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.ExpenseRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

class ActivityViewModel(
    expenses: ExpenseRepository,
    people: PersonRepository,
) : ViewModel() {
    data class State(val expenses: List<Expense>, val nameById: Map<PersonId, String>, val me: PersonId?)

    val state: StateFlow<State> = combine(expenses.observeRecent(100), people.observeAll()) { exps, ppl ->
        State(
            expenses = exps,
            nameById = ppl.associate { it.id to it.displayName },
            me = ppl.firstOrNull { it.isMe }?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State(emptyList(), emptyMap(), null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onOpenExpense: (ExpenseId) -> Unit) {
    val vm: ActivityViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val me = state.me
    Scaffold(topBar = { TopAppBar(title = { Text("Activity", fontWeight = FontWeight.SemiBold) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.expenses.isEmpty() || me == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Nothing yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your latest expenses, payments, and comments will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(state.expenses, key = { it.id.value }) { e ->
                        ExpenseRow(expense = e, me = me, nameById = state.nameById, onClick = { onOpenExpense(e.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}
