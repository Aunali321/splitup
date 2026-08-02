package app.splitup.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Money

/**
 * Right-aligned balance summary for list rows: each currency gets its own
 * sign-dependent label and amount, or a muted [emptyLabel] when nothing is owed.
 */
@Composable
fun BalanceCell(
    balances: Map<Currency, Money>,
    positiveLabel: String,
    negativeLabel: String,
    emptyLabel: String,
) {
    Column(horizontalAlignment = Alignment.End) {
        if (balances.isEmpty()) {
            Text(
                emptyLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            balances.values.forEach { amount ->
                Text(
                    if (amount.isPositive) positiveLabel else negativeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BalanceText(amount = amount, bold = true)
            }
        }
    }
}
