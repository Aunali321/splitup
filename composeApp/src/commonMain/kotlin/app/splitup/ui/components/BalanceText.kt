package app.splitup.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.splitup.shared.domain.model.Money
import app.splitup.ui.theme.NegativeBalance
import app.splitup.ui.theme.NegativeBalanceDark
import app.splitup.ui.theme.PositiveBalance
import app.splitup.ui.theme.PositiveBalanceDark

/**
 * Render an amount with semantic colour: positive = green ("you are owed"),
 * negative = red ("you owe"), zero = surface-variant text. Respects dark mode.
 */
@Composable
fun BalanceText(
    amount: Money,
    modifier: Modifier = Modifier,
    label: String? = null,
    bold: Boolean = false,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = when {
        amount.isPositive -> if (isDark) PositiveBalanceDark else PositiveBalance
        amount.isNegative -> if (isDark) NegativeBalanceDark else NegativeBalance
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val display = buildString {
        if (label != null) {
            append(label); append(' ')
        }
        append(amount.abs().format())
    }
    Text(
        text = display,
        color = color,
        modifier = modifier,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    )
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue
