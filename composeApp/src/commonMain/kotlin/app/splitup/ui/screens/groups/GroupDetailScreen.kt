package app.splitup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.debt.Debt
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
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
        val myNetByCurrency: Map<Currency, Money>,
    )

    val state: StateFlow<State> = combine(
        groups.observe(groupId),
        expenses.observeInGroup(groupId),
        people.observeAll(),
    ) { g, exps, ppl ->
        val me = ppl.firstOrNull { it.isMe }?.id
        val perCurrency = DebtSimplifier.balances(exps)
        val myNet: Map<Currency, Money> = me?.let { id ->
            perCurrency.mapValues { (currency, balances) -> balances[id] ?: Money.zero(currency) }
        } ?: emptyMap()
        State(
            group = g,
            expenses = exps,
            debts = DebtSimplifier.simplify(DebtSimplifier.debtsFromExpenses(exps)),
            nameById = ppl.associate { it.id to it.displayName },
            me = me,
            myNetByCurrency = myNet.filterValues { !it.isZero },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State(null, emptyList(), emptyList(), emptyMap(), null, emptyMap()))
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
    val group = state.group
    val me = state.me

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO group settings */ }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
        if (group == null || me == null) return@Scaffold

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                GroupHero(
                    group = group,
                    myNet = state.myNetByCurrency,
                    onSettleUp = onSettleUp,
                )
            }
            val groupedByMonth = state.expenses
                .filter { !it.isDeleted }
                .groupBy { it.date.monthHeader() }
            groupedByMonth.forEach { (month, monthExpenses) ->
                item(key = "month-$month") {
                    Text(
                        month,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(monthExpenses, key = { it.id.value }) { e ->
                    val payerId = e.shares.firstOrNull { it.paidShare.isPositive }?.personId
                    val payerName = payerId?.let { state.nameById[it] } ?: "Someone"
                    ExpenseRow(expense = e, me = me, payerName = payerName, onClick = { onOpenExpense(e.id) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun GroupHero(group: Group, myNet: Map<Currency, Money>, onSettleUp: () -> Unit) {
    val tint = groupTint(group.name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    groupTypeIcon(group.type),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${group.members.size} people") },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                }
            }
        }

        if (myNet.isEmpty()) {
            StatusPill(
                icon = Icons.Rounded.Check,
                text = "You are all settled up in this group",
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                myNet.forEach { (_, money) ->
                    val label = if (money.isPositive) "You are owed" else "You owe"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        BalanceText(amount = money, bold = true)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = onSettleUp,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Handshake, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Settle up")
            }
            FilledTonalButton(
                onClick = { /* TODO balances detail */ },
                modifier = Modifier.weight(1f),
            ) { Text("Balances") }
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
    }
}

private fun groupTypeIcon(type: GroupType): ImageVector = when (type) {
    GroupType.HOME, GroupType.APARTMENT, GroupType.HOUSE -> Icons.Rounded.Home
    GroupType.TRIP -> Icons.Rounded.Flight
    else -> Icons.Rounded.Groups
}

private val tints = listOf(
    Color(0xFFE65100), Color(0xFF1B7F44), Color(0xFF7A4F9C),
    Color(0xFFC2185B), Color(0xFF1976D2), Color(0xFF00897B),
    Color(0xFFD84315), Color(0xFF455A64),
)

private fun groupTint(name: String): Color {
    var h = 0
    for (c in name) h = 31 * h + c.code
    return tints[(h.rem(tints.size) + tints.size).rem(tints.size)]
}

private fun LocalDate.monthHeader(): String {
    val m = when (monthNumber) {
        1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"; 5 -> "May"; 6 -> "June"
        7 -> "July"; 8 -> "August"; 9 -> "September"; 10 -> "October"; 11 -> "November"; else -> "December"
    }
    return "$m $year"
}
