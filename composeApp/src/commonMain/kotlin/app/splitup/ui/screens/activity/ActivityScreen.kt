package app.splitup.ui.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.BalanceText
import app.splitup.ui.components.CategoryIcon
import app.splitup.ui.components.ExpenseRow
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.PersonAvatar
import app.splitup.ui.components.SearchField
import app.splitup.ui.util.formatEventTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Instant
import org.koin.compose.viewmodel.koinViewModel

class ActivityViewModel(
    expenses: ExpenseRepository,
    groups: GroupRepository,
    people: PersonRepository,
) : ViewModel() {

    enum class Kind { ADDED, UPDATED, DELETED }

    /** One feed entry: the latest thing that happened to an expense. */
    data class Event(val expense: Expense, val kind: Kind, val at: Instant)

    data class State(
        val events: List<Event> = emptyList(),
        val searchResults: List<Expense> = emptyList(),
        val personById: Map<PersonId, Person> = emptyMap(),
        val groupById: Map<GroupId, Group> = emptyMap(),
        val me: PersonId? = null,
    )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Search results are a snapshot per query, like Splitwise's; the feed stays live.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val results: Flow<List<Expense>> = _query.flatMapLatest { q ->
        if (q.isBlank()) flow { emit(emptyList()) } else flow { emit(expenses.search(q.trim())) }
    }

    val state: StateFlow<State> = combine(
        expenses.observeFeed(100),
        results,
        groups.observeAll(includeArchived = true),
        people.observeAll(),
    ) { feed, found, gs, ppl ->
        State(
            events = feed.map { e ->
                when {
                    e.deletedAt != null -> Event(e, Kind.DELETED, e.deletedAt!!)
                    e.updatedAt != e.createdAt -> Event(e, Kind.UPDATED, e.updatedAt)
                    else -> Event(e, Kind.ADDED, e.createdAt)
                }
            },
            searchResults = found,
            personById = ppl.associateBy { it.id },
            groupById = gs.associateBy { it.id },
            me = ppl.firstOrNull { it.isMe }?.id,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setQuery(q: String) {
        _query.value = q
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onOpenExpense: (ExpenseId) -> Unit) {
    val vm: ActivityViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val me = state.me
    var searching by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (searching) SearchField(query = query, onQueryChange = vm::setQuery) },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) vm.setQuery("")
                    }) {
                        Icon(
                            if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searching) "Close search" else "Search",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                me == null -> {}
                query.isNotBlank() -> SearchResults(state, me, onOpenExpense)
                state.events.isEmpty() -> EmptyState(
                    title = "Nothing yet",
                    body = "Your latest expenses and payments will show up here.",
                )
                else -> LazyColumn {
                    item(key = "header") {
                        Text(
                            "Recent activity",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    items(state.events, key = { it.expense.id.value }) { event ->
                        EventRow(event = event, state = state, me = me, onClick = { onOpenExpense(event.expense.id) })
                        ListDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: ActivityViewModel.State,
    me: PersonId,
    onOpenExpense: (ExpenseId) -> Unit,
) {
    if (state.searchResults.isEmpty()) {
        EmptyState(title = "No results found", body = "No expenses match your search.")
        return
    }
    LazyColumn {
        items(state.searchResults, key = { it.id.value }) { e ->
            ExpenseRow(
                expense = e,
                me = me,
                nameById = state.personById.mapValues { it.value.displayName },
                onClick = { onOpenExpense(e.id) },
            )
            ListDivider()
        }
    }
}

@Composable
private fun EventRow(
    event: ActivityViewModel.Event,
    state: ActivityViewModel.State,
    me: PersonId,
    onClick: () -> Unit,
) {
    val e = event.expense
    val actor = state.personById[e.createdBy]
    val actorName = when {
        e.createdBy == me -> "You"
        else -> actor?.displayName ?: "Someone"
    }
    val groupName = e.groupId?.let { state.groupById[it]?.name }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box {
            if (e.isPayment) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                CategoryIcon(e.categoryId, size = 44.dp)
            }
            actor?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(1.dp),
                ) {
                    PersonAvatar(it, size = 20.dp)
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(actorName) }
                    append(
                        when (event.kind) {
                            ActivityViewModel.Kind.ADDED -> if (e.isPayment) " recorded a payment" else " added "
                            ActivityViewModel.Kind.UPDATED -> if (e.isPayment) " updated a payment" else " updated "
                            ActivityViewModel.Kind.DELETED -> if (e.isPayment) " deleted a payment" else " deleted "
                        },
                    )
                    if (!e.isPayment) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("“${e.description}”") }
                    }
                    groupName?.let {
                        append(" in ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("“$it”") }
                    }
                    append(".")
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            ImpactLine(e, me)
            Text(
                event.at.formatEventTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImpactLine(e: Expense, me: PersonId) {
    if (e.isPayment) {
        val payer = e.shares.firstOrNull { it.paidShare.isPositive }?.personId
        val recipient = e.shares.firstOrNull { it.owedShare.isPositive && it.paidShare.isZero }?.personId
        when (me) {
            payer -> BalanceText(amount = e.cost, label = "You paid", bold = true)
            recipient -> BalanceText(amount = -e.cost, label = "You received", bold = true)
            else -> {}
        }
        return
    }
    val net = e.balanceFor(me)
    when {
        net.isPositive -> BalanceText(amount = net, label = "You get back", bold = true)
        net.isNegative -> BalanceText(amount = net, label = "You owe", bold = true)
        else -> {}
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
