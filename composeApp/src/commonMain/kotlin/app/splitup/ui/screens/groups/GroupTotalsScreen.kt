package app.splitup.ui.screens.groups

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Category
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.DefaultCategories
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.CategoryIcon
import app.splitup.ui.components.ListDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class GroupTotalsViewModel(
    groupId: GroupId,
    groups: GroupRepository,
    expenses: ExpenseRepository,
    people: PersonRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    enum class Period(val label: String) {
        THIS_MONTH("This month"),
        LAST_MONTH("Last month"),
        ALL_TIME("All time"),
    }

    /** Group spending summary for one currency present in the period. */
    data class Totals(
        val currency: Currency,
        val totalSpent: Money,
        val yourShare: Money,
        val youPaidFor: Money,
        val paymentsMade: Money,
        val paymentsReceived: Money,
        /** Spending per parent category, largest first. */
        val byCategory: List<Pair<Category, Money>>,
    )

    data class State(
        val groupName: String = "",
        val period: Period = Period.ALL_TIME,
        val totals: List<Totals> = emptyList(),
    )

    private val period = MutableStateFlow(Period.ALL_TIME)

    val state: StateFlow<State> = combine(
        groups.observe(groupId),
        expenses.observeInGroup(groupId),
        people.observeAll(),
        period,
    ) { g, exps, ppl, p ->
        val me = ppl.firstOrNull { it.isMe }?.id
        State(
            groupName = g?.name.orEmpty(),
            period = p,
            totals = me?.let { totalsOf(exps.filter { e -> inPeriod(e.date, p) }, it) }.orEmpty(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setPeriod(p: Period) {
        period.value = p
    }

    private fun inPeriod(date: LocalDate, p: Period): Boolean {
        val today = clock.now().toLocalDateTime(timeZone).date
        return when (p) {
            Period.ALL_TIME -> true
            Period.THIS_MONTH -> date.year == today.year && date.month == today.month
            Period.LAST_MONTH -> {
                val lastMonth = today.minus(DatePeriod(months = 1))
                date.year == lastMonth.year && date.month == lastMonth.month
            }
        }
    }

    private fun totalsOf(expenses: List<Expense>, me: PersonId): List<Totals> =
        expenses.groupBy { it.currency }.map { (currency, inCurrency) ->
            val zero = Money.zero(currency)
            fun sumOf(subset: List<Expense>, amount: (Expense) -> Money) =
                subset.fold(zero) { acc, e -> acc + amount(e) }

            val (payments, charges) = inCurrency.partition { it.isPayment }
            Totals(
                currency = currency,
                totalSpent = sumOf(charges) { it.cost },
                yourShare = sumOf(charges) { e -> e.shares.firstOrNull { it.personId == me }?.owedShare ?: zero },
                youPaidFor = sumOf(charges) { e -> e.shares.firstOrNull { it.personId == me }?.paidShare ?: zero },
                paymentsMade = sumOf(payments) { e -> e.shares.firstOrNull { it.personId == me }?.paidShare ?: zero },
                paymentsReceived = sumOf(payments) { e -> e.shares.firstOrNull { it.personId == me }?.owedShare ?: zero },
                byCategory = charges
                    .groupBy { e ->
                        val category = DefaultCategories.get(e.categoryId)
                        category.parentId?.let { DefaultCategories.get(it) } ?: category
                    }
                    .map { (parent, exps) -> parent to sumOf(exps) { it.cost } }
                    .sortedByDescending { it.second.minorUnits },
            )
        }.sortedBy { it.currency.code }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupTotalsScreen(groupId: String, onBack: () -> Unit) {
    val vm: GroupTotalsViewModel = koinViewModel(parameters = { parametersOf(GroupId(groupId)) })
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Totals", fontWeight = FontWeight.SemiBold)
                        if (state.groupName.isNotEmpty()) {
                            Text(
                                state.groupName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GroupTotalsViewModel.Period.entries.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = p == state.period,
                        onClick = { vm.setPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(index, GroupTotalsViewModel.Period.entries.size),
                    ) { Text(p.label) }
                }
            }

            if (state.totals.isEmpty()) {
                Text(
                    "No expenses in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.totals.forEach { totals ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.totals.size > 1) {
                        Text(
                            totals.currency.code,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    // Splitwise's local visuals: a donut for all-time, category bars for a month.
                    if (state.period == GroupTotalsViewModel.Period.ALL_TIME) {
                        ShareDonut(totals)
                    }
                    if (totals.byCategory.isNotEmpty() && state.period != GroupTotalsViewModel.Period.ALL_TIME) {
                        CategoryBars(totals.byCategory)
                    }
                    StatRow("Total group spending", totals.totalSpent)
                    StatRow("Total you paid for", totals.youPaidFor)
                    StatRow("Your total share", totals.yourShare)
                    ListDivider()
                    StatRow("Payments made", totals.paymentsMade)
                    StatRow("Payments received", totals.paymentsReceived)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Horizontal bar list: spending per parent category, scaled to the largest. */
@Composable
private fun CategoryBars(byCategory: List<Pair<Category, Money>>) {
    val max = byCategory.maxOf { it.second.minorUnits }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        byCategory.forEach { (category, amount) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(category.id, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text(
                            category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            amount.format(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(amount.minorUnits.toFloat() / max)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

/** All-time donut: your share of the group's spending, total in the center. */
@Composable
private fun ShareDonut(totals: GroupTotalsViewModel.Totals) {
    val fraction = if (totals.totalSpent.minorUnits == 0L) 0f
    else totals.yourShare.minorUnits.toFloat() / totals.totalSpent.minorUnits
    val ring = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(200.dp).padding(12.dp)) {
                val stroke = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                )
                if (fraction > 0f) {
                    drawArc(
                        color = ring,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                        useCenter = false,
                        style = stroke,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Total spent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    totals.totalSpent.format(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(ring))
            Spacer(Modifier.width(6.dp))
            Text(
                "Your share ${totals.yourShare.format()} · ${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, amount: Money) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            amount.format(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
