package app.splitup.ui.screens.friends

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.ExpenseRow
import app.splitup.ui.components.PersonAvatar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class FriendDetailViewModel(
    private val friendId: PersonId,
    expenses: ExpenseRepository,
    people: PersonRepository,
) : ViewModel() {
    data class State(val friend: Person?, val expenses: List<Expense>, val me: PersonId?)

    val state: StateFlow<State> = combine(
        people.observe(friendId),
        expenses.observeWithFriend(friendId),
        people.observeAll(),
    ) { friend, exps, all ->
        State(friend, exps, all.firstOrNull { it.isMe }?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State(null, emptyList(), null))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailScreen(
    friendId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onSettleUp: () -> Unit,
    onOpenExpense: (ExpenseId) -> Unit,
) {
    val vm: FriendDetailViewModel = koinViewModel(parameters = { parametersOf(PersonId(friendId)) })
    val state by vm.state.collectAsStateWithLifecycle()
    val me = state.me
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.friend?.displayName.orEmpty(), fontWeight = FontWeight.SemiBold) },
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
                    state.friend?.let { friend ->
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            PersonAvatar(friend, size = 56.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(friend.displayName, style = MaterialTheme.typography.titleLarge)
                                friend.email?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        OutlinedButton(onClick = onSettleUp, modifier = Modifier.padding(start = 16.dp)) {
                            Text("Settle up")
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
                items(state.expenses, key = { it.id.value }) { e ->
                    if (me != null) {
                        ExpenseRow(expense = e, me = me, nameById = emptyMap(), onClick = { onOpenExpense(e.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}
