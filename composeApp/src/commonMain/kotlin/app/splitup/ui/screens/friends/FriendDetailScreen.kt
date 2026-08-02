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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.BalanceText
import app.splitup.ui.components.ExpenseRow
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.PersonAvatar
import app.splitup.ui.components.SearchField
import app.splitup.ui.util.matching
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class FriendDetailViewModel(
    private val friendId: PersonId,
    expenses: ExpenseRepository,
    private val people: PersonRepository,
) : ViewModel() {
    data class State(
        val friend: Person? = null,
        /** Everything the two of you share — non-group and group expenses alike. */
        val expenses: List<Expense> = emptyList(),
        val me: PersonId? = null,
        val nameById: Map<PersonId, String> = emptyMap(),
        /** Positive = friend owes me, per currency. */
        val balance: Map<Currency, Money> = emptyMap(),
        /** Removal deletes the person row, so any expense involvement blocks it. */
        val removable: Boolean = false,
    )

    val state: StateFlow<State> = combine(
        people.observe(friendId),
        expenses.observeAll(),
        people.observeAll(),
    ) { friend, exps, all ->
        val me = all.firstOrNull { it.isMe }?.id
        val shared = if (me == null) emptyList() else exps.filter { e ->
            e.shares.any { it.personId == friendId } && e.shares.any { it.personId == me }
        }
        State(
            friend = friend,
            expenses = shared,
            me = me,
            nameById = all.associate { it.id to it.displayName },
            balance = me?.let { DebtSimplifier.balancesToward(exps, it)[friendId] }.orEmpty(),
            removable = exps.none { e -> e.shares.any { it.personId == friendId } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun remove(onDone: () -> Unit) {
        viewModelScope.launch {
            people.delete(friendId)
            onDone()
        }
    }
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
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showRemove by remember { mutableStateOf(false) }

    if (showRemove) {
        val name = state.friend?.displayName.orEmpty()
        if (state.removable) {
            AlertDialog(
                onDismissRequest = { showRemove = false },
                title = { Text("Remove $name?") },
                text = { Text("$name will disappear from your friends list.") },
                confirmButton = {
                    TextButton(onClick = {
                        showRemove = false
                        vm.remove(onBack)
                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showRemove = false }) { Text("Cancel") } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { showRemove = false },
                title = { Text("Can't remove $name") },
                text = { Text("You have shared expenses with $name. Their history has to stay so balances keep adding up.") },
                confirmButton = { TextButton(onClick = { showRemove = false }) { Text("OK") } },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        SearchField(query = query, onQueryChange = { query = it })
                    } else {
                        Text(state.friend?.displayName.orEmpty(), fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(
                            if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searching) "Close search" else "Search expenses with friend",
                        )
                    }
                    IconButton(onClick = { showRemove = true }) {
                        Icon(Icons.Outlined.PersonRemove, contentDescription = "Remove from friends")
                    }
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
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            if (state.balance.isEmpty()) {
                                Text(
                                    "You are all settled up",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                state.balance.values.forEach { net ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (net.isPositive) "${friend.firstName} owes you " else "You owe ${friend.firstName} ",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        BalanceText(amount = net, bold = true)
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onSettleUp,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        ) { Text("Settle up") }
                        ListDivider(modifier = Modifier.padding(top = 16.dp))
                    }
                }
                val visible = state.expenses.matching(query)
                if (visible.isEmpty() && query.isNotBlank()) {
                    item(key = "no-results") {
                        Text(
                            "No results found",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                items(visible, key = { it.id.value }) { e ->
                    if (me != null) {
                        ExpenseRow(expense = e, me = me, nameById = state.nameById, onClick = { onOpenExpense(e.id) })
                        ListDivider()
                    }
                }
            }
        }
    }
}
