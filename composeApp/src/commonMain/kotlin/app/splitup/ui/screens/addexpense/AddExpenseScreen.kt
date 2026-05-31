package app.splitup.ui.screens.addexpense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.PersonId
import app.splitup.ui.components.CurrencyPicker
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String?,
    friendId: String?,
    expenseId: String? = null,
    onDone: () -> Unit,
    onOpenPaidBy: () -> Unit,
    onOpenSplit: () -> Unit,
) {
    val vm: AddExpenseViewModel = koinInject()
    androidx.compose.runtime.LaunchedEffect(groupId, friendId, expenseId) {
        vm.start(groupId?.let { GroupId(it) }, friendId?.let { PersonId(it) }, expenseId)
    }
    val editing by vm.editing.collectAsStateWithLifecycle()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val draftReady by vm.draftReady.collectAsStateWithLifecycle()
    if (!draftReady) return
    val draftState by vm.draft.state.collectAsStateWithLifecycle()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showCurrencyPicker) {
        CurrencyPicker(
            onPick = { vm.draft.setCurrency(it); showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    if (showDatePicker) {
        // DatePicker works in UTC day-millis; convert both ways in UTC to avoid
        // an off-by-one when the device is behind/ahead of UTC.
        val zone = TimeZone.UTC
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = draftState.date.atStartOfDayIn(zone).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        vm.draft.setDate(Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }

    val canSave = draftState.description.isNotBlank() &&
        draftState.amount.isNotBlank() &&
        draftState.payers.isNotEmpty() &&
        draftState.participants.isNotEmpty() &&
        vm.draft.splitError() == null &&
        vm.draft.payerError() == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing != null) "Edit expense" else "Add expense", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        enabled = canSave,
                        onClick = {
                            vm.submit(
                                groupId = groupId?.let { GroupId(it) },
                                friendId = friendId?.let { PersonId(it) },
                                onDone = onDone,
                            )
                        },
                    ) { Icon(Icons.Outlined.Check, contentDescription = "Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "With ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "you ",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "and: ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(scope.scopeLabel) },
                    leadingIcon = scope.leadingIcon,
                )
            }

            FieldRow(
                icon = Icons.Outlined.Description,
                value = draftState.description,
                placeholder = "Enter a description",
                onValueChange = vm.draft::setDescription,
            )

            CurrencyAmountRow(
                currency = draftState.currency,
                value = draftState.amount,
                onValueChange = vm.draft::setAmount,
                onCurrencyClick = { showCurrencyPicker = true },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Paid by ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = onOpenPaidBy,
                    label = { Text(vm.payerLabel(scope, draftState)) },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "and split ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = onOpenSplit,
                    label = { Text(draftState.strategy.label) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "On ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(formatExpenseDate(draftState.date)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            ScopeFooter(scope.scopeLabel)
        }
    }
}

@Composable
private fun FieldRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.titleMedium) },
            textStyle = MaterialTheme.typography.titleMedium,
            singleLine = true,
            colors = transparentFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CurrencyAmountRow(
    currency: Currency,
    value: String,
    onValueChange: (String) -> Unit,
    onCurrencyClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onCurrencyClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                currency.symbol,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("0${decimalSep(currency)}00", style = MaterialTheme.typography.headlineMedium) },
            textStyle = MaterialTheme.typography.headlineMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = transparentFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ScopeFooter(scopeLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CurrencyExchange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(scopeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
)

private fun decimalSep(currency: Currency): String = if (currency.decimals == 0) "" else "."

private fun formatExpenseDate(date: LocalDate): String {
    val month = when (date.monthNumber) {
        1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
        7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
    }
    return "${date.dayOfMonth} $month ${date.year}"
}

private val AddExpenseDraft.SplitMode.label: String
    get() = when (this) {
        AddExpenseDraft.SplitMode.Equal -> "equally"
        AddExpenseDraft.SplitMode.Unequally -> "unequally"
        AddExpenseDraft.SplitMode.Percent -> "by percentages"
        AddExpenseDraft.SplitMode.Shares -> "by shares"
    }
