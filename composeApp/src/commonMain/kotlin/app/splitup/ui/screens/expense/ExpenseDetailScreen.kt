package app.splitup.ui.screens.expense

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ExpenseDetailViewModel(
    private val expenseId: ExpenseId,
    private val expenses: ExpenseRepository,
    private val people: PersonRepository,
) : ViewModel() {
    data class State(val expense: Expense? = null, val nameById: Map<String, String> = emptyMap())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val e = expenses.get(expenseId)
            val names = people.observeAll().first().associate { it.id.value to it.displayName }
            _state.value = State(e, names)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(expenseId: String, onBack: () -> Unit) {
    val vm: ExpenseDetailViewModel = koinViewModel(parameters = { parametersOf(ExpenseId(expenseId)) })
    val state by vm.state.collectAsState()
    val e = state.expense
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(e?.description.orEmpty(), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            e?.let {
                Text(it.cost.format(), style = MaterialTheme.typography.displaySmall)
                Text("on ${it.date}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it.notes.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text("Split", style = MaterialTheme.typography.titleSmall)
                it.shares.forEach { s ->
                    val name = state.nameById[s.personId.value] ?: s.personId.value
                    Text(
                        "$name — paid ${s.paidShare.format()}, owed ${s.owedShare.format()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
