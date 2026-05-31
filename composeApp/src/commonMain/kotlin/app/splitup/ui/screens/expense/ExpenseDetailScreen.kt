package app.splitup.ui.screens.expense

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.ExpenseShare
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.BalanceText
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ExpenseDetailViewModel(
    private val expenseId: ExpenseId,
    private val expenses: ExpenseRepository,
    private val people: PersonRepository,
) : ViewModel() {
    data class State(
        val expense: Expense? = null,
        val nameById: Map<PersonId, String> = emptyMap(),
        val me: PersonId? = null,
    )

    val state: StateFlow<State> = combine(
        expenses.observe(expenseId),
        people.observeAll(),
    ) { e, all ->
        State(
            expense = e,
            nameById = all.associate { it.id to it.displayName },
            me = all.firstOrNull { it.isMe }?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            expenses.softDelete(expenseId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(expenseId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm: ExpenseDetailViewModel = koinViewModel(parameters = { parametersOf(ExpenseId(expenseId)) })
    val state by vm.state.collectAsState()
    val e = state.expense
    val me = state.me
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (e?.isPayment == true) "Delete payment?" else "Delete expense?") },
            text = { Text("This removes it for everyone in the split. You can still find it in your activity.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.delete(onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (e?.isPayment == true) "Payment" else "Expense", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (e?.isPayment == false) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Outlined.EditNote, contentDescription = "Edit")
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (e == null || me == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ExpenseHero(expense = e)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            SplitBreakdown(expense = e, me = me, nameById = state.nameById)

            e.notes?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    "Added by ${state.nameById[e.createdBy] ?: "someone"} · ${e.createdAt.toLocalDate()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (e.updatedAt != e.createdAt) {
                    Text(
                        "Last edited ${e.updatedAt.toLocalDate()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ExpenseHero(expense: Expense) {
    val isPayment = expense.isPayment
    val tint = if (isPayment) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val onTint = if (isPayment) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.35f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPayment) Icons.Rounded.Handshake else Icons.Outlined.Receipt,
                    contentDescription = null,
                    tint = onTint,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    expense.date.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            expense.cost.format(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SplitBreakdown(expense: Expense, me: PersonId, nameById: Map<PersonId, String>) {
    val payers = expense.shares.filter { it.paidShare.isPositive }
    val owers = expense.shares.filter { it.owedShare.isPositive && it != payers.firstOrNull() }

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Split", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        payers.forEach { share ->
            val name = if (share.personId == me) "You" else nameById[share.personId] ?: "Someone"
            PayerRow(name = name, amount = share.paidShare.format(), isYou = share.personId == me)
        }

        if (expense.isPayment) {
            val recipient = expense.shares.firstOrNull { it.owedShare.isPositive && it.paidShare.isZero }
            if (recipient != null) {
                val name = if (recipient.personId == me) "you" else nameById[recipient.personId] ?: "someone"
                BreakdownLine(name = "→ $name receives", amount = recipient.owedShare.format(), isYou = recipient.personId == me)
            }
        } else {
            expense.shares
                .filter { it.owedShare.isPositive }
                .forEach { share ->
                    val youOwn = share.personId == me
                    val name = if (youOwn) "You owe" else "${nameById[share.personId] ?: "Someone"} owes"
                    BreakdownLine(name = name, amount = share.owedShare.format(), isYou = youOwn)
                }
        }
    }
}

@Composable
private fun PayerRow(name: String, amount: String, isYou: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "$name paid",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            amount,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BreakdownLine(name: String, amount: String, isYou: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isYou) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isYou) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isYou) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isYou) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun kotlinx.datetime.Instant.toLocalDate(): kotlinx.datetime.LocalDate =
    toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
