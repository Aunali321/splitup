package app.splitup.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SimpleFormDialog(
    title: String,
    primaryLabel: String,
    fields: List<FormField>,
    onSubmit: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember(fields) {
        fields.associate { it.key to mutableStateOf("") }
    }
    val canSubmit = fields.all { f ->
        !f.required || state.getValue(f.key).value.isNotBlank()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEach { f ->
                    var value by state.getValue(f.key)
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(f.label) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(state.mapValues { (_, v) -> v.value.trim() }) },
                enabled = canSubmit,
            ) { Text(primaryLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

data class FormField(val key: String, val label: String, val required: Boolean = false)
