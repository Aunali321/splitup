package app.splitup.ui.screens.addexpense

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Money
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.PersonAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPickerScreen(
    vm: AddExpenseViewModel,
    onBack: () -> Unit,
) {
    val scope by vm.scope.collectAsStateWithLifecycle()
    val draftReady by vm.draftReady.collectAsStateWithLifecycle()
    if (!draftReady) return
    val state by vm.draft.state.collectAsStateWithLifecycle()
    val mode = state.strategy
    val error = vm.draft.splitError()

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
                        onClick = {
                            vm.draft.setStrategy(m)
                            // Splitwise's non-equal tabs list everyone; only Equally has checkboxes.
                            if (m != AddExpenseDraft.SplitMode.Equal) {
                                vm.draft.setParticipants(scope.members.map { it.id })
                            }
                        },
                        text = { Text(m.tabLabel, maxLines = 1) },
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(mode.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    mode.explainer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(scope.members, key = { it.id.value }) { person ->
                        MemberSplitRow(
                            name = if (person.isMe) "${person.displayName} (you)" else person.displayName,
                            included = person.id in state.participants,
                            mode = mode,
                            currencySymbol = state.currency.symbol,
                            owedPreview = vm.draft.owedPreview(person.id),
                            input = state.splitInputs[person.id].orEmpty(),
                            avatar = { PersonAvatar(person) },
                            onToggle = { vm.draft.toggleParticipant(person.id) },
                            onInputChange = { vm.draft.setSplitInput(person.id, it) },
                        )
                        ListDivider()
                    }
                }
            }

            SplitFooter(
                vm = vm,
                mode = mode,
                allSelected = scope.members.all { it.id in state.participants },
                onToggleAll = { all ->
                    vm.draft.setParticipants(if (all) scope.members.map { it.id } else emptyList())
                },
                error = error,
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
    currencySymbol: String,
    owedPreview: Money,
    input: String,
    avatar: @Composable () -> Unit,
    onToggle: () -> Unit,
    onInputChange: (String) -> Unit,
) {
    val equalMode = mode == AddExpenseDraft.SplitMode.Equal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (equalMode) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            // Splitwise shows the computed owed amount under the name for the
            // derived modes; on Unequally the field itself is the amount.
            if (mode != AddExpenseDraft.SplitMode.Equal && mode != AddExpenseDraft.SplitMode.Unequally) {
                Text(
                    owedPreview.format(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (mode) {
            AddExpenseDraft.SplitMode.Equal ->
                Checkbox(checked = included, onCheckedChange = { onToggle() })
            AddExpenseDraft.SplitMode.Unequally ->
                InlineField(input, onInputChange, prefix = currencySymbol, decimal = true)
            AddExpenseDraft.SplitMode.Percent ->
                InlineField(input, onInputChange, suffix = "%", decimal = true)
            AddExpenseDraft.SplitMode.Shares ->
                InlineField(input, onInputChange, suffix = "shares", decimal = false)
            AddExpenseDraft.SplitMode.Adjustment ->
                InlineField(input, onInputChange, prefix = "+", decimal = true)
        }
    }
}

/** Underlined right-aligned inline field, Splitwise's row-input style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    prefix: String? = null,
    suffix: String? = null,
    decimal: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        prefix?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(4.dp))
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(if (decimal) "0.00" else "0", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            },
            textStyle = TextStyle(textAlign = TextAlign.End).merge(MaterialTheme.typography.titleMedium),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            modifier = Modifier.width(96.dp),
        )
        suffix?.let {
            Spacer(Modifier.width(4.dp))
            Text(it, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Mode-specific tally: per-person for Equal (+ All toggle), entered-of-total otherwise. */
@Composable
private fun SplitFooter(
    vm: AddExpenseViewModel,
    mode: AddExpenseDraft.SplitMode,
    allSelected: Boolean,
    onToggleAll: (Boolean) -> Unit,
    error: String?,
) {
    val state by vm.draft.state.collectAsStateWithLifecycle()
    val total = runCatching { Money.parse(state.amount, state.currency) }.getOrNull()
        ?: Money.zero(state.currency)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (mode) {
            AddExpenseDraft.SplitMode.Equal -> {
                val n = state.participants.size
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (n == 0) "Select people" else "${Money.ofMinor(total.minorUnits / n, state.currency).format()}/person",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "($n ${if (n == 1) "person" else "people"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("All", style = MaterialTheme.typography.titleMedium)
                Checkbox(checked = allSelected, onCheckedChange = onToggleAll)
            }
            AddExpenseDraft.SplitMode.Unequally -> TallyColumn(
                primary = "${vm.draft.enteredTotal().format()} of ${total.format()}",
                secondary = error ?: "${(total - vm.draft.enteredTotal()).format()} left",
                isError = error != null,
            )
            AddExpenseDraft.SplitMode.Percent -> {
                val entered = vm.draft.enteredBasisPoints()
                TallyColumn(
                    primary = "${AddExpenseDraft.formatPercent(entered)}% of 100%",
                    secondary = error
                        ?: "${AddExpenseDraft.formatPercent(AddExpenseDraft.TOTAL_BP - entered)}% left",
                    isError = error != null,
                )
            }
            AddExpenseDraft.SplitMode.Shares -> TallyColumn(
                primary = "${vm.draft.enteredShares()} total shares",
                secondary = error,
                isError = error != null,
            )
            AddExpenseDraft.SplitMode.Adjustment -> TallyColumn(
                primary = "${vm.draft.enteredTotal().format()} in adjustments",
                secondary = error ?: "remainder is split equally",
                isError = error != null,
            )
        }
    }
}

@Composable
private fun TallyColumn(primary: String, secondary: String?, isError: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        secondary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val AddExpenseDraft.SplitMode.tabLabel: String
    get() = when (this) {
        AddExpenseDraft.SplitMode.Equal -> "Equally"
        AddExpenseDraft.SplitMode.Unequally -> "Unequally"
        AddExpenseDraft.SplitMode.Percent -> "By percentages"
        AddExpenseDraft.SplitMode.Shares -> "By shares"
        AddExpenseDraft.SplitMode.Adjustment -> "By adjustment"
    }

private val AddExpenseDraft.SplitMode.title: String
    get() = when (this) {
        AddExpenseDraft.SplitMode.Equal -> "Split equally"
        AddExpenseDraft.SplitMode.Unequally -> "Split by exact amounts"
        AddExpenseDraft.SplitMode.Percent -> "Split by percentages"
        AddExpenseDraft.SplitMode.Shares -> "Split by shares"
        AddExpenseDraft.SplitMode.Adjustment -> "Split by adjustment"
    }

private val AddExpenseDraft.SplitMode.explainer: String
    get() = when (this) {
        AddExpenseDraft.SplitMode.Equal -> "Select which people owe an equal share."
        AddExpenseDraft.SplitMode.Unequally -> "Specify exactly how much each person owes."
        AddExpenseDraft.SplitMode.Percent -> "Enter the percentage split that's fair for your situation."
        AddExpenseDraft.SplitMode.Shares ->
            "Great for time-based splitting (2 nights → 2 shares) and splitting across families (family of 3 → 3 shares)."
        AddExpenseDraft.SplitMode.Adjustment ->
            "Enter adjustments to reflect who owes extra; SplitUp will distribute the remainder equally."
    }
