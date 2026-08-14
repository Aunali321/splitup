package app.splitup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.debt.Debt
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.BalanceText
import app.splitup.ui.components.PersonAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class GroupBalancesViewModel(
    groupId: GroupId,
    groups: GroupRepository,
    expenses: ExpenseRepository,
    people: PersonRepository,
) : ViewModel() {

    data class MemberBalance(
        val person: Person,
        /** Net per currency: positive = gets back, negative = owes. */
        val net: Map<Currency, Money>,
    )

    data class State(
        val members: List<MemberBalance> = emptyList(),
        val repayments: List<Debt> = emptyList(),
        val personById: Map<PersonId, Person> = emptyMap(),
        val me: PersonId? = null,
        val simplifyOn: Boolean = true,
    )

    val state: StateFlow<State> = combine(
        groups.observe(groupId),
        expenses.observeInGroup(groupId),
        people.observeAll(),
    ) { g, exps, ppl ->
        val balances = DebtSimplifier.balances(exps)
        val members = g?.members.orEmpty().mapNotNull { member ->
            ppl.firstOrNull { it.id == member.personId }?.let { person ->
                MemberBalance(
                    person = person,
                    net = balances.mapNotNull { (currency, byPerson) ->
                        byPerson[person.id]?.takeIf { !it.isZero }?.let { currency to it }
                    }.toMap(),
                )
            }
        }
        val pairwise = DebtSimplifier.debtsFromExpenses(exps)
        val simplify = g?.simplifyByDefault == true
        State(
            members = members,
            repayments = if (simplify) DebtSimplifier.simplify(pairwise) else pairwise,
            personById = ppl.associateBy { it.id },
            me = ppl.firstOrNull { it.isMe }?.id,
            simplifyOn = simplify,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())
}

/** Splitwise's per-group Balances screen: member rows with expandable repayments. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupBalancesScreen(
    groupId: String,
    onBack: () -> Unit,
    onSettleUp: () -> Unit,
) {
    val vm: GroupBalancesViewModel = koinViewModel(parameters = { parametersOf(GroupId(groupId)) })
    val state by vm.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(setOf<PersonId>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Balances", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (state.simplifyOn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Simplify debts is turned on in this group.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(state.members, key = { it.person.id.value }) { member ->
                val isExpanded = member.person.id in expanded
                MemberRow(
                    member = member,
                    me = state.me,
                    expanded = isExpanded,
                    onToggle = {
                        expanded = if (isExpanded) expanded - member.person.id else expanded + member.person.id
                    },
                )
                if (isExpanded) {
                    val involving = state.repayments.filter {
                        it.from == member.person.id || it.to == member.person.id
                    }
                    if (involving.isEmpty()) {
                        Text(
                            "No suggested repayments.",
                            modifier = Modifier.fillMaxWidth().padding(start = 76.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    involving.forEach { debt ->
                        RepaymentRow(
                            debt = debt,
                            me = state.me,
                            personById = state.personById,
                            onSettleUp = onSettleUp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupBalancesViewModel.MemberBalance,
    me: PersonId?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(member.person, size = 44.dp)
        Spacer(Modifier.width(16.dp))
        val name = if (member.person.id == me) "You" else member.person.displayName
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (member.net.isEmpty()) {
                Text(
                    "$name ${if (member.person.id == me) "are" else "is"} settled up",
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                member.net.values.forEach { net ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$name ${verbFor(member.person.id == me, net)} ",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        BalanceText(amount = net, bold = true)
                    }
                }
            }
        }
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun verbFor(isMe: Boolean, net: Money): String = when {
    net.isPositive -> if (isMe) "get back" else "gets back"
    else -> if (isMe) "owe" else "owes"
}

@Composable
private fun RepaymentRow(
    debt: Debt,
    me: PersonId?,
    personById: Map<PersonId, Person>,
    onSettleUp: () -> Unit,
) {
    val from = personById[debt.from]
    val fromName = if (debt.from == me) "You" else from?.displayName ?: "Someone"
    val toName = if (debt.to == me) "you" else personById[debt.to]?.displayName ?: "someone"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        from?.let { PersonAvatar(it, size = 24.dp) }
        Spacer(Modifier.width(12.dp))
        Text(
            "$fromName ${if (debt.from == me) "owe" else "owes"} $toName ${debt.amount.format()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onSettleUp) { Text("Settle up") }
    }
}
