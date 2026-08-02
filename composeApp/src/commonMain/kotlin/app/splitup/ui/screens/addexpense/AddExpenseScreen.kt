package app.splitup.ui.screens.addexpense

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.ui.components.CategoryIcon
import app.splitup.ui.components.CategoryPicker
import app.splitup.ui.components.CurrencyPicker
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    vm: AddExpenseViewModel,
    onDone: () -> Unit,
    onOpenPaidBy: () -> Unit,
    onOpenSplit: () -> Unit,
) {
    val editing by vm.editing.collectAsStateWithLifecycle()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val draftReady by vm.draftReady.collectAsStateWithLifecycle()
    val scopeGone by vm.scopeGone.collectAsStateWithLifecycle()
    LaunchedEffect(scopeGone) { if (scopeGone) onDone() }
    if (!draftReady) return
    val draftState by vm.draft.state.collectAsStateWithLifecycle()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var showReceiptDialog by remember { mutableStateOf(false) }

    if (showCurrencyPicker) {
        CurrencyPicker(
            onPick = { vm.draft.setCurrency(it); showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false },
        )
    }

    if (showCategoryPicker) {
        CategoryPicker(
            onPick = { vm.draft.setCategory(it); showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false },
        )
    }

    if (showDateDialog) {
        DateRepeatDialog(
            initialDate = draftState.date,
            repeat = draftState.repeat,
            onRepeatChange = vm.draft::setRepeat,
            onPick = vm.draft::setDate,
            onDismiss = { showDateDialog = false },
        )
    }

    if (showNotesDialog) {
        NotesDialog(
            initial = draftState.notes,
            onSave = { vm.draft.setNotes(it); showNotesDialog = false },
            onDismiss = { showNotesDialog = false },
        )
    }

    if (showReceiptDialog) {
        ReceiptDialog(
            hasReceipt = draftState.receiptUrl != null,
            canTakePhoto = vm.imagePicker.canTakePhoto,
            onTakePhoto = { vm.attachReceipt(fromCamera = true); showReceiptDialog = false },
            onPickImage = { vm.attachReceipt(fromCamera = false); showReceiptDialog = false },
            onRemove = { vm.removeReceipt(); showReceiptDialog = false },
            onDismiss = { showReceiptDialog = false },
        )
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
                        onClick = { vm.submit(onDone) },
                    ) { Icon(Icons.Outlined.Check, contentDescription = "Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            DetailBar(
                scopeLabel = scope.scopeLabel,
                showReceipt = vm.imagePicker.canPickImage,
                onDate = { showDateDialog = true },
                onReceipt = { showReceiptDialog = true },
                onNotes = { showNotesDialog = true },
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
                )
            }

            DescriptionRow(
                categoryId = draftState.categoryId,
                value = draftState.description,
                onValueChange = vm.draft::setDescription,
                onCategoryClick = { showCategoryPicker = true },
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

            val inlineError = if (draftState.description.isBlank() || draftState.amount.isBlank()) null
            else vm.draft.payerError() ?: vm.draft.splitError()
            inlineError?.let {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            // Date and repeat surface in the bottom detail bar dialogs; show the
            // choices here only once they differ from the defaults.
            val summary = buildList {
                if (draftState.repeat != RepeatInterval.NEVER) add(draftState.repeat.label)
                if (draftState.receiptUrl != null) add("Receipt attached")
                if (draftState.notes.isNotBlank()) add("Notes added")
            }
            if (summary.isNotEmpty()) {
                Text(
                    summary.joinToString(" · "),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Splitwise's bottom detail bar: context on the left, date/camera/notes on the right. */
@Composable
private fun DetailBar(
    scopeLabel: String,
    showReceipt: Boolean,
    onDate: () -> Unit,
    onReceipt: () -> Unit,
    onNotes: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            scopeLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDate) {
            Icon(Icons.Outlined.Event, contentDescription = "Date and repeat")
        }
        if (showReceipt) {
            IconButton(onClick = onReceipt) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = "Receipt")
            }
        }
        IconButton(onClick = onNotes) {
            Icon(Icons.Outlined.EditNote, contentDescription = "Notes")
        }
    }
}

/** Calendar plus Splitwise's repeat options, one dialog like their date screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRepeatDialog(
    initialDate: LocalDate,
    repeat: RepeatInterval,
    onRepeatChange: (RepeatInterval) -> Unit,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works in UTC day-millis; converting both ways in UTC avoids
    // an off-by-one when the device is behind/ahead of UTC.
    val zone = TimeZone.UTC
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDayIn(zone).toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date)
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column {
            DatePicker(state = state, modifier = Modifier.weight(1f, fill = false))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                REPEAT_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = option == repeat,
                        onClick = { onRepeatChange(option) },
                        label = { Text(if (option == RepeatInterval.NEVER) "Off" else option.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DescriptionRow(
    categoryId: CategoryId,
    value: String,
    onValueChange: (String) -> Unit,
    onCategoryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(
            categoryId = categoryId,
            size = 48.dp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onCategoryClick),
        )
        Spacer(Modifier.width(12.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Enter a description", style = MaterialTheme.typography.titleMedium) },
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
            placeholder = {
                Text(
                    if (currency.decimals == 0) "0" else "0.00",
                    style = MaterialTheme.typography.headlineMedium,
                )
            },
            textStyle = MaterialTheme.typography.headlineMedium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = transparentFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReceiptDialog(
    hasReceipt: Boolean,
    canTakePhoto: Boolean,
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receipt") },
        text = {
            Column {
                if (canTakePhoto) ReceiptOption("Take photo", onTakePhoto)
                ReceiptOption("Choose image", onPickImage)
                if (hasReceipt) ReceiptOption("Remove receipt", onRemove, MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReceiptOption(label: String, onClick: () -> Unit, color: Color? = null) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = color ?: MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun NotesDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notes") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Add notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

private val AddExpenseDraft.SplitMode.label: String
    get() = when (this) {
        AddExpenseDraft.SplitMode.Equal -> "equally"
        AddExpenseDraft.SplitMode.Unequally -> "unequally"
        AddExpenseDraft.SplitMode.Percent -> "by percentages"
        AddExpenseDraft.SplitMode.Shares -> "by shares"
        AddExpenseDraft.SplitMode.Adjustment -> "by adjustment"
    }

// Splitwise's repeat options; DAILY exists in the model for imports but is not offered.
private val REPEAT_OPTIONS = listOf(
    RepeatInterval.NEVER,
    RepeatInterval.WEEKLY,
    RepeatInterval.FORTNIGHTLY,
    RepeatInterval.MONTHLY,
    RepeatInterval.YEARLY,
)

internal val RepeatInterval.label: String
    get() = when (this) {
        RepeatInterval.NEVER -> "Doesn't repeat"
        RepeatInterval.DAILY -> "Daily"
        RepeatInterval.WEEKLY -> "Weekly"
        RepeatInterval.FORTNIGHTLY -> "Fortnightly"
        RepeatInterval.MONTHLY -> "Monthly"
        RepeatInterval.YEARLY -> "Yearly"
    }
