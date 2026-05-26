package app.splitup.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.splitup.shared.domain.model.Currency

@Composable
fun AmountInput(
    currency: Currency,
    value: String,
    onValueChange: (String) -> Unit,
    onCurrencyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AssistChip(
            onClick = onCurrencyClick,
            label = { Text(currency.code, style = MaterialTheme.typography.titleMedium) },
            modifier = Modifier.padding(end = 8.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                val filtered = raw.filterIndexed { i, c -> c.isDigit() || (c == '.' && raw.indexOf('.') == i) }
                onValueChange(filtered)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            placeholder = { Text("0.00", style = MaterialTheme.typography.headlineMedium) },
            textStyle = MaterialTheme.typography.headlineMedium,
            singleLine = true,
        )
    }
}
