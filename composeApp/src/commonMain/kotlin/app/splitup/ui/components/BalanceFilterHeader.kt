package app.splitup.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Money

/** Splitwise's balance-list filters, shared by the Friends and Groups tabs. */
enum class BalanceFilter {
    ALL,
    OUTSTANDING,
    YOU_OWE,
    OWES_YOU,
    ;

    /** [noun] is "friends" or "groups"; [owesYou] the tab's phrasing for creditors. */
    fun label(noun: String, owesYou: String): String = when (this) {
        ALL -> "All $noun"
        OUTSTANDING -> "Outstanding balances"
        YOU_OWE -> "${noun.replaceFirstChar { it.uppercase() }} you owe"
        OWES_YOU -> owesYou
    }

    /** Whether a row with this net position stays visible under the filter. */
    fun matches(balance: Map<Currency, Money>): Boolean = when (this) {
        ALL -> true
        OUTSTANDING -> balance.isNotEmpty()
        YOU_OWE -> balance.values.any { it.isNegative }
        OWES_YOU -> balance.values.any { it.isPositive }
    }
}

/**
 * Overall-balance header with the filter popup: "You are owed X overall" plus a
 * tune icon, and a "Showing only …" caption while a filter is active.
 */
@Composable
fun BalanceFilterHeader(
    overall: Map<Currency, Money>,
    filter: BalanceFilter,
    onFilterChange: (BalanceFilter) -> Unit,
    noun: String,
    owesYouLabel: String,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (overall.isEmpty()) {
                    Text(
                        "You are all settled up",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    overall.values.forEach { net ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (net.isPositive) "You are owed " else "You owe ",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            BalanceText(amount = net, bold = true)
                            Text(" overall", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Filter")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    BalanceFilter.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .clickable {
                                    onFilterChange(option)
                                    menuOpen = false
                                }
                                .padding(end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = option == filter,
                                onClick = {
                                    onFilterChange(option)
                                    menuOpen = false
                                },
                            )
                            Text(option.label(noun, owesYouLabel), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        if (filter != BalanceFilter.ALL) {
            Text(
                "Showing only ${filter.label(noun, owesYouLabel).lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Empty state for an active filter that matches nothing. */
@Composable
fun FilterEmptyState(onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No one to see here.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClear) { Text("Clear filter") }
    }
}

/** "Hiding N settled-up …" collapsing row for long-settled entries. */
@Composable
fun SettledUpExpander(
    hiddenCount: Int,
    noun: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (expanded) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Previously settled $noun.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onToggle) { Text("Re-hide") }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Hiding $noun that have been settled up over one month.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onToggle) {
                Text("Show $hiddenCount settled-up ${if (hiddenCount == 1) noun.dropLast(1) else noun}")
            }
        }
    }
}
