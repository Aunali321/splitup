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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Money
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.PersonAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaidByPickerScreen(
    vm: AddExpenseViewModel,
    onBack: () -> Unit,
) {
    val scope by vm.scope.collectAsStateWithLifecycle()
    val draftReady by vm.draftReady.collectAsStateWithLifecycle()
    if (!draftReady) return
    val state by vm.draft.state.collectAsStateWithLifecycle()
    val multiple = state.multiplePayers
    val error = vm.draft.payerError()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Who paid?", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (multiple) {
                        IconButton(enabled = error == null, onClick = onBack) {
                            Icon(Icons.Outlined.Check, contentDescription = "Done")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !multiple,
                    onClick = { vm.draft.setMultiplePayers(false) },
                    label = { Text("One person") },
                )
                FilterChip(
                    selected = multiple,
                    onClick = { vm.draft.setMultiplePayers(true) },
                    label = { Text("Multiple people") },
                )
            }
            ListDivider()

            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(scope.members, key = { it.id.value }) { person ->
                        val name = if (person.isMe) "${person.displayName} (you)" else person.displayName
                        if (multiple) {
                            PayerAmountRow(
                                name = name,
                                symbol = state.currency.symbol,
                                input = state.payerInputs[person.id].orEmpty(),
                                avatar = { PersonAvatar(person) },
                                onInputChange = { vm.draft.setPayerInput(person.id, it) },
                            )
                        } else {
                            val selected = person.id == state.payers.keys.firstOrNull()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.draft.setSinglePayer(person.id)
                                        onBack()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PersonAvatar(person)
                                Spacer(Modifier.width(16.dp))
                                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (selected) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        ListDivider()
                    }
                }
            }

            if (multiple) {
                val total = state.payers.values.fold(Money.zero(state.currency)) { acc, m -> acc + m }
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column {
                        Text(
                            error ?: "Payments add up",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (error == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "${total.format()} entered",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayerAmountRow(
    name: String,
    symbol: String,
    input: String,
    avatar: @Composable () -> Unit,
    onInputChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(16.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(symbol, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(4.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.width(116.dp),
            singleLine = true,
            placeholder = { Text("0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}
