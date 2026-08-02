package app.splitup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.GroupType
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.BalanceText
import app.splitup.ui.components.ExpenseRow
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.MonthHeader
import app.splitup.ui.components.SearchField
import app.splitup.ui.theme.groupTint
import app.splitup.ui.util.matching
import app.splitup.ui.util.monthHeader
import coil3.compose.AsyncImage
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
        val nameById: Map<PersonId, String>,
        val me: PersonId?,
        val myNetByCurrency: Map<Currency, Money>,
    )

    val state: StateFlow<State> = combine(
        groups.observe(groupId),
        expenses.observeInGroup(groupId),
        people.observeAll(),
    ) { g, exps, ppl ->
        val me = ppl.firstOrNull { it.isMe }?.id
        State(
            group = g,
            expenses = exps,
            nameById = ppl.associate { it.id to it.displayName },
            me = me,
            myNetByCurrency = me?.let { DebtSimplifier.netOf(exps, it) }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State(null, emptyList(), emptyMap(), null, emptyMap()))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onSettleUp: () -> Unit,
    onOpenExpense: (ExpenseId) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBalances: () -> Unit,
    onOpenTotals: () -> Unit,
    onOpenWhiteboard: () -> Unit,
) {
    val vm: GroupDetailViewModel = koinViewModel(parameters = { parametersOf(GroupId(groupId)) })
    val state by vm.state.collectAsStateWithLifecycle()
    val group = state.group
    val me = state.me
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (searching) {
                TopAppBar(
                    title = { SearchField(query = query, onQueryChange = { query = it }) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searching = false
                            query = ""
                        }) { Icon(Icons.Outlined.Close, contentDescription = "Close search") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddExpense,
                icon = { Icon(Icons.Outlined.ReceiptLong, contentDescription = null) },
                text = { Text("Add expense") },
            )
        },
    ) { padding ->
        if (group == null || me == null) return@Scaffold

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!searching) {
                item(key = "header") {
                    GroupHeader(
                        group = group,
                        onBack = onBack,
                        onSearch = { searching = true },
                        onSettings = onOpenSettings,
                    )
                }
            }
            item(key = "summary") {
                GroupSummary(
                    myNet = state.myNetByCurrency,
                    onSettleUp = onSettleUp,
                    onOpenBalances = onOpenBalances,
                    onOpenCharts = onOpenTotals,
                    onOpenWhiteboard = onOpenWhiteboard,
                )
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
            val groupedByMonth = visible.groupBy { it.date.monthHeader() }
            groupedByMonth.forEach { (month, monthExpenses) ->
                item(key = "month-$month") {
                    MonthHeader(month)
                }
                items(monthExpenses, key = { it.id.value }) { e ->
                    ExpenseRow(expense = e, me = me, nameById = state.nameById, onClick = { onOpenExpense(e.id) })
                    ListDivider()
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun GroupHeader(
    group: Group,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val tint = groupTint(group.name)
    Box(modifier = Modifier.fillMaxWidth().height(230.dp)) {
        if (group.coverUrl != null) {
            AsyncImage(
                model = group.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.7f)))),
            )
        }
        // Scrim keeps the overlaid name readable on any backdrop.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Outlined.Search, "Search in group", onSearch)
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Outlined.Settings, "Settings", onSettings)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        ) {
            Text(
                group.name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    groupTypeIconFor(group),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${group.members.size} ${if (group.members.size == 1) "person" else "people"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun GroupSummary(
    myNet: Map<Currency, Money>,
    onSettleUp: () -> Unit,
    onOpenBalances: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenWhiteboard: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (myNet.isEmpty()) {
            Text(
                "\ud83c\udf89 You are all settled up in this group.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                myNet.forEach { (_, money) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (money.isPositive) "You are owed" else "You owe",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        BalanceText(amount = money, bold = true)
                    }
                }
            }
        }

        // Splitwise's action carousel: Settle up, Charts, Balances, Whiteboard.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onSettleUp) {
                Icon(Icons.Outlined.Handshake, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Settle up")
            }
            FilledTonalButton(onClick = onOpenCharts) { Text("Charts") }
            FilledTonalButton(onClick = onOpenBalances) { Text("Balances") }
            FilledTonalButton(onClick = onOpenWhiteboard) { Text("Whiteboard") }
        }
    }
}

private fun groupTypeIconFor(group: Group): ImageVector = when (group.type) {
    GroupType.HOME, GroupType.APARTMENT, GroupType.HOUSE -> Icons.Rounded.Home
    GroupType.TRIP -> Icons.Rounded.Flight
    GroupType.COUPLE -> Icons.Rounded.Favorite
    else -> Icons.Rounded.Groups
}
