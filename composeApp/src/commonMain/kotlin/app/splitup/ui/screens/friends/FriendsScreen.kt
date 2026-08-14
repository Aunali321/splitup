package app.splitup.ui.screens.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.util.IdGenerator
import app.splitup.ui.components.BalanceCell
import app.splitup.ui.components.BalanceFilter
import app.splitup.ui.components.BalanceFilterHeader
import app.splitup.ui.components.FilterEmptyState
import app.splitup.ui.components.FormField
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.PersonAvatar
import app.splitup.ui.components.SearchField
import app.splitup.ui.components.SettledUpExpander
import app.splitup.ui.components.SimpleFormDialog
import app.splitup.ui.util.staleSettledCutoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

class FriendsViewModel(
    private val people: PersonRepository,
    expenses: ExpenseRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    data class Row(val person: Person, val balance: Map<Currency, Money>)

    data class State(
        val rows: List<Row> = emptyList(),
        /** Settled up with no activity for over a month — collapsed behind an expander. */
        val staleSettled: List<Row> = emptyList(),
        /** My net position per currency across everything: positive = I am owed. */
        val overall: Map<Currency, Money> = emptyMap(),
        val filter: BalanceFilter = BalanceFilter.ALL,
    )

    private val _filter = MutableStateFlow(BalanceFilter.ALL)

    val state: StateFlow<State> = combine(
        people.observeAll(),
        expenses.observeAll(),
        _filter,
    ) { ppl, exps, filter ->
        val me = ppl.firstOrNull { it.isMe }?.id
        val friends = ppl.filter { !it.isMe }
        val toward = me?.let { DebtSimplifier.balancesToward(exps, it) }.orEmpty()
        val all = friends.map { Row(it, toward[it.id].orEmpty()) }

        if (filter != BalanceFilter.ALL) {
            State(
                rows = all.filter { filter.matches(it.balance) },
                overall = me?.let { DebtSimplifier.netOf(exps, it) }.orEmpty(),
                filter = filter,
            )
        } else {
            val lastActivity = buildMap<PersonId, LocalDate> {
                exps.forEach { e -> e.shares.forEach { s -> merge(s.personId, e.date, ::maxOf) } }
            }
            val cutoff = staleSettledCutoff(clock, timeZone)
            val (stale, active) = all.partition { row ->
                row.balance.isEmpty() && (lastActivity[row.person.id]?.let { it < cutoff } == true)
            }
            State(
                rows = active,
                staleSettled = stale,
                overall = me?.let { DebtSimplifier.netOf(exps, it) }.orEmpty(),
                filter = filter,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setFilter(filter: BalanceFilter) {
        _filter.value = filter
    }

    fun addFriend(firstName: String, lastName: String?, email: String?) {
        viewModelScope.launch {
            people.save(
                Person(
                    id = PersonId(idGenerator.next()),
                    firstName = firstName,
                    lastName = lastName?.ifBlank { null },
                    email = email?.ifBlank { null },
                    isMe = false,
                    isRegistered = false,
                    updatedAt = clock.now(),
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(onOpenFriend: (PersonId) -> Unit, onAddExpense: () -> Unit) {
    val vm: FriendsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    if (showAdd) {
        SimpleFormDialog(
            title = "Add friend",
            primaryLabel = "Add",
            fields = listOf(
                FormField("first", "First name", required = true),
                FormField("last", "Last name"),
                FormField("email", "Email"),
            ),
            onSubmit = { values ->
                vm.addFriend(values.getValue("first"), values["last"], values["email"])
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (searching) SearchField(query = query, onQueryChange = { query = it }) },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(
                            if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searching) "Close search" else "Search",
                        )
                    }
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = "Add friend")
                    }
                },
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
        var showSettled by remember { mutableStateOf(false) }
        // A search covers hidden settled rows too — filtering only what's on screen
        // would make long-settled friends unfindable.
        val visibleRows = if (query.isBlank()) state.rows
        else (state.rows + state.staleSettled).filter { it.person.displayName.contains(query.trim(), true) }
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "overall") {
                    BalanceFilterHeader(
                        overall = state.overall,
                        filter = state.filter,
                        onFilterChange = vm::setFilter,
                        noun = "friends",
                        owesYouLabel = "Friends who owe you",
                    )
                }
                if (visibleRows.isEmpty() && state.filter != BalanceFilter.ALL) {
                    item(key = "filter-empty") { FilterEmptyState(onClear = { vm.setFilter(BalanceFilter.ALL) }) }
                }
                items(visibleRows, key = { it.person.id.value }) { row ->
                    FriendRow(row = row, onClick = { onOpenFriend(row.person.id) })
                    ListDivider()
                }
                if (state.staleSettled.isNotEmpty() && query.isBlank()) {
                    item(key = "settled-expander") {
                        SettledUpExpander(
                            hiddenCount = state.staleSettled.size,
                            noun = "friends",
                            expanded = showSettled,
                            onToggle = { showSettled = !showSettled },
                        )
                    }
                    if (showSettled) {
                        items(state.staleSettled, key = { it.person.id.value }) { row ->
                            FriendRow(row = row, onClick = { onOpenFriend(row.person.id) })
                            ListDivider()
                        }
                    }
                }
                item(key = "add-friends") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = { showAdd = true }) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add more friends")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(row: FriendsViewModel.Row, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(row.person)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.person.displayName, style = MaterialTheme.typography.titleMedium)
            row.person.email?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BalanceCell(
            balances = row.balance,
            positiveLabel = "owes you",
            negativeLabel = "you owe",
            emptyLabel = "settled up",
        )
    }
}
