package app.splitup.ui.screens.addexpense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.ui.components.PersonAvatar
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPickerScreen(
    groupId: String?,
    friendId: String?,
    expenseId: String? = null,
    onBack: () -> Unit,
) {
    val vm: AddExpenseViewModel = koinInject()
    LaunchedEffect(groupId, friendId, expenseId) {
        vm.start(groupId?.let { GroupId(it) }, friendId?.let { PersonId(it) }, expenseId)
    }
    val scope by vm.scope.collectAsStateWithLifecycle()
    val draftReady by vm.draftReady.collectAsStateWithLifecycle()
    if (!draftReady) return
    val state by vm.draft.state.collectAsStateWithLifecycle()
    val mode = state.strategy
    val error = vm.draft.splitError()

    val perPerson = remember(state.amount, state.participants.size, state.currency) {
        val n = state.participants.size
        if (n == 0 || state.amount.isBlank()) Money.zero(state.currency)
        else runCatching {
            val total = Money.parse(state.amount, state.currency)
            Money.ofMinor(total.minorUnits / n, state.currency)
        }.getOrDefault(Money.zero(state.currency))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adjust split", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(enabled = error == null, onClick = onBack) {
                        Icon(Icons.Outlined.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(
                selectedTabIndex = mode.ordinal,
                edgePadding = 16.dp,
            ) {
                AddExpenseDraft.SplitMode.entries.forEach { m ->
                    Tab(
                        selected = mode == m,
                        onClick = { vm.draft.setStrategy(m) },
                        text = { Text(m.tabLabel, maxLines = 1) },
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(scope.members, key = { it.id.value }) { person ->
                        val included = person.id in state.participants
                        MemberSplitRow(
                            name = if (person.isMe) "${person.displayName} (you)" else person.displayName,
                            included = included,
                            mode = mode,
                            currency = state.currency,
                            input = state.splitInputs[person.id].orEmpty(),
                            avatar = { PersonAvatar(person) },
                            onToggle = { vm.draft.toggleParticipant(person.id) },
                            onInputChange = { vm.draft.setSplitInput(person.id, it) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }

            SplitFooter(
                primary = when {
                    mode == AddExpenseDraft.SplitMode.Equal -> "${perPerson.format()}/person"
                    error == null -> "Splits add up"
                    else -> error
                },
                primaryColor = if (mode == AddExpenseDraft.SplitMode.Equal || error == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                secondary = vm.draft.splitSummary(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberSplitRow(
    name: String,
    included: Boolean,
    mode: AddExpenseDraft.SplitMode,
    currency: Currency,
    input: String,
    avatar: @Composable () -> Unit,
    onToggle: () -> Unit,
    onInputChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(16.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (included && mode != AddExpenseDraft.SplitMode.Equal) {
            if (mode == AddExpenseDraft.SplitMode.Unequally) {
                Text(currency.symbol, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(4.dp))
            }
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.width(if (mode == AddExpenseDraft.SplitMode.Shares) 88.dp else 116.dp),
                singleLine = true,
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (mode == AddExpenseDraft.SplitMode.Shares) KeyboardType.Number else KeyboardType.Decimal,
                ),
            )
            when (mode) {
                AddExpenseDraft.SplitMode.Percent -> { Spacer(Modifier.width(4.dp)); Text("%", style = MaterialTheme.typography.titleMedium) }
                AddExpenseDraft.SplitMode.Shares -> { Spacer(Modifier.width(4.dp)); Text("×", style = MaterialTheme.typography.titleMedium) }
                else -> {}
            }
            Spacer(Modifier.width(8.dp))
        }
        Checkbox(checked = included, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SplitFooter(primary: String, primaryColor: androidx.compose.ui.graphics.Color, secondary: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = primaryColor)
            Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val AddExpenseDraft.SplitMode.tabLabel: String
    get() = when (this) {
        AddExpenseDraft.SplitMode.Equal -> "Equally"
        AddExpenseDraft.SplitMode.Unequally -> "Unequally"
        AddExpenseDraft.SplitMode.Percent -> "By percentages"
        AddExpenseDraft.SplitMode.Shares -> "By shares"
    }
