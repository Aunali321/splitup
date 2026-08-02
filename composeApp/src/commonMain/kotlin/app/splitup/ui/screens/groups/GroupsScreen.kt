package app.splitup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.GroupMember
import app.splitup.shared.domain.model.GroupRole
import app.splitup.shared.domain.model.GroupType
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.util.IdGenerator
import app.splitup.ui.components.BalanceCell
import app.splitup.ui.components.BalanceFilter
import app.splitup.ui.components.BalanceFilterHeader
import app.splitup.ui.components.FilterEmptyState
import app.splitup.ui.components.GroupAvatar
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.SearchField
import app.splitup.ui.components.SettledUpExpander
import app.splitup.ui.util.staleSettledCutoff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

class GroupsViewModel(
    private val groups: GroupRepository,
    private val people: PersonRepository,
    expenses: ExpenseRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    data class Row(
        val group: Group,
        /** My net position per currency inside this group: positive = I am owed. */
        val myNet: Map<Currency, Money>,
        val hasExpenses: Boolean,
    )

    data class State(
        val rows: List<Row> = emptyList(),
        /** Settled up with no activity for over a month — collapsed behind an expander. */
        val staleSettled: List<Row> = emptyList(),
        val overall: Map<Currency, Money> = emptyMap(),
        /** My net across expenses outside any group — the pseudo-group row. */
        val nonGroupNet: Map<Currency, Money> = emptyMap(),
        val hasNonGroup: Boolean = false,
        val filter: BalanceFilter = BalanceFilter.ALL,
    )

    private val _filter = MutableStateFlow(BalanceFilter.ALL)

    val state: StateFlow<State> = combine(
        groups.observeAll(),
        expenses.observeAll(),
        people.observeAll(),
        _filter,
    ) { gs, exps, ppl, filter ->
        val me = ppl.firstOrNull { it.isMe }?.id
        val byGroup = exps.groupBy { it.groupId }
        val all = gs.map { g ->
            val groupExpenses = byGroup[g.id].orEmpty()
            Row(
                group = g,
                myNet = me?.let { DebtSimplifier.netOf(groupExpenses, it) }.orEmpty(),
                hasExpenses = groupExpenses.isNotEmpty(),
            )
        }
        val overall = me?.let { DebtSimplifier.netOf(exps, it) }.orEmpty()
        val nonGroup = byGroup[null].orEmpty()
        val nonGroupNet = me?.let { DebtSimplifier.netOf(nonGroup, it) }.orEmpty()

        if (filter != BalanceFilter.ALL) {
            State(
                rows = all.filter { filter.matches(it.myNet) },
                overall = overall,
                nonGroupNet = nonGroupNet,
                hasNonGroup = nonGroup.isNotEmpty(),
                filter = filter,
            )
        } else {
            val cutoff = staleSettledCutoff(clock, timeZone)
            val (stale, active) = all.partition { row ->
                row.myNet.isEmpty() && row.hasExpenses &&
                    byGroup[row.group.id].orEmpty().maxOf { it.date } < cutoff
            }
            State(
                rows = active,
                staleSettled = stale,
                overall = overall,
                nonGroupNet = nonGroupNet,
                hasNonGroup = nonGroup.isNotEmpty(),
                filter = filter,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setFilter(filter: BalanceFilter) {
        _filter.value = filter
    }

    fun createGroup(name: String, type: GroupType, onCreated: (GroupId) -> Unit = {}) {
        viewModelScope.launch {
            val me = people.observeAll().first().firstOrNull { it.isMe } ?: return@launch
            val now = clock.now()
            val id = GroupId(idGenerator.next())
            groups.save(
                Group(
                    id = id,
                    name = name,
                    type = type,
                    members = listOf(GroupMember(me.id, GroupRole.OWNER, now)),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            onCreated(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onOpenGroup: (GroupId) -> Unit,
    onOpenNonGroup: () -> Unit,
    onAddExpense: () -> Unit,
) {
    val vm: GroupsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    if (showCreate) {
        CreateGroupDialog(
            onCreate = { name, type ->
                vm.createGroup(name, type)
                showCreate = false
            },
            onDismiss = { showCreate = false },
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
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Outlined.GroupAdd, contentDescription = "Create a group")
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
        // would make long-settled groups unfindable.
        val visibleRows = if (query.isBlank()) state.rows
        else (state.rows + state.staleSettled).filter { it.group.name.contains(query.trim(), true) }
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "overall") {
                    BalanceFilterHeader(
                        overall = state.overall,
                        filter = state.filter,
                        onFilterChange = vm::setFilter,
                        noun = "groups",
                        owesYouLabel = "Groups that owe you",
                    )
                }
                if (query.isBlank() && state.filter == BalanceFilter.ALL) {
                    item(key = "non-group") {
                        NonGroupRow(
                            net = state.nonGroupNet,
                            hasExpenses = state.hasNonGroup,
                            onClick = onOpenNonGroup,
                        )
                        ListDivider()
                    }
                }
                if (visibleRows.isEmpty() && state.filter != BalanceFilter.ALL) {
                    item(key = "filter-empty") { FilterEmptyState(onClear = { vm.setFilter(BalanceFilter.ALL) }) }
                }
                items(visibleRows, key = { it.group.id.value }) { row ->
                    GroupRow(row = row, onClick = { onOpenGroup(row.group.id) })
                    ListDivider()
                }
                if (state.staleSettled.isNotEmpty() && query.isBlank()) {
                    item(key = "settled-expander") {
                        SettledUpExpander(
                            hiddenCount = state.staleSettled.size,
                            noun = "groups",
                            expanded = showSettled,
                            onToggle = { showSettled = !showSettled },
                        )
                    }
                    if (showSettled) {
                        items(state.staleSettled, key = { it.group.id.value }) { row ->
                            GroupRow(row = row, onClick = { onOpenGroup(row.group.id) })
                            ListDivider()
                        }
                    }
                }
                item(key = "start-group") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = { showCreate = true }) {
                            Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start a new group")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NonGroupRow(
    net: Map<Currency, Money>,
    hasExpenses: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            "Non-group expenses",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        BalanceCell(
            balances = net,
            positiveLabel = "you are owed",
            negativeLabel = "you owe",
            emptyLabel = if (hasExpenses) "settled up" else "no expenses",
        )
    }
}

@Composable
private fun CreateGroupDialog(onCreate: (String, GroupType) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GroupType.OTHER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Type", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    creatableTypes.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), type) },
                enabled = name.trim().isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val creatableTypes = listOf(GroupType.TRIP, GroupType.HOME, GroupType.COUPLE, GroupType.OTHER)

private val GroupType.label: String
    get() = when (this) {
        GroupType.TRIP -> "Trip"
        GroupType.HOME -> "Home"
        GroupType.COUPLE -> "Couple"
        else -> "Other"
    }

@Composable
private fun GroupRow(row: GroupsViewModel.Row, onClick: () -> Unit) {
    val group = row.group
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupAvatar(group)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${group.members.size} member${if (group.members.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BalanceCell(
            balances = row.myNet,
            positiveLabel = "you are owed",
            negativeLabel = "you owe",
            emptyLabel = if (row.hasExpenses) "settled up" else "no expenses",
        )
    }
}


