package app.splitup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.ui.util.monthAbbr
import kotlinx.datetime.LocalDate

@Composable
fun ExpenseRow(
    expense: Expense,
    me: PersonId,
    nameById: Map<PersonId, String>,
    onClick: () -> Unit,
) {
    if (expense.isPayment) {
        PaymentRow(expense, me, nameById, onClick)
    } else {
        val payers = expense.shares.filter { it.paidShare.isPositive }
        val payerName = when {
            payers.size > 1 -> "${payers.size} people"
            else -> payers.firstOrNull()?.personId?.let {
                if (it == me) "You" else nameById[it]
            } ?: "Someone"
        }
        ChargeRow(expense, me, payerName, onClick)
    }
}

@Composable
private fun ChargeRow(
    expense: Expense,
    me: PersonId,
    payerName: String,
    onClick: () -> Unit,
) {
    val net: Money = expense.balanceFor(me)
    val involved = expense.shares.any { it.personId == me }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateBadge(expense.date)
        Spacer(Modifier.width(12.dp))
        CategoryIcon(expense.categoryId)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = expense.description,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$payerName paid ${expense.cost.format()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            when {
                !involved -> Text(
                    "not involved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                net.isPositive -> {
                    Text("you lent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BalanceText(amount = net, bold = true)
                }
                net.isNegative -> {
                    Text("you borrowed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BalanceText(amount = net, bold = true)
                }
                else -> Text(
                    "settled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaymentRow(
    expense: Expense,
    me: PersonId,
    nameById: Map<PersonId, String>,
    onClick: () -> Unit,
) {
    val payer = expense.shares.firstOrNull { it.paidShare.isPositive }?.personId
    val recipient = expense.shares.firstOrNull { it.owedShare.isPositive && it.paidShare.isZero }?.personId
    val payerName = payer?.let { if (it == me) "You" else nameById[it] } ?: "Someone"
    val recipientName = recipient?.let { if (it == me) "you" else nameById[it] } ?: "someone"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateBadge(expense.date)
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Payments,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$payerName paid $recipientName ${expense.cost.format()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DateBadge(date: LocalDate) {
    Column(
        modifier = Modifier
            .size(width = 40.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            monthAbbr(date.monthNumber),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
