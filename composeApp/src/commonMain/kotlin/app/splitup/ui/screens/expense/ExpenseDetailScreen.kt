package app.splitup.ui.screens.expense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.Comment
import app.splitup.shared.domain.model.CommentId
import app.splitup.shared.domain.model.DefaultCategories
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.repository.CommentRepository
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.util.IdGenerator
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.PersonAvatar
import app.splitup.ui.components.categoryVectorFor
import app.splitup.ui.platform.ImagePicker
import app.splitup.ui.platform.isLocalReceipt
import app.splitup.ui.util.formatMedium
import app.splitup.ui.util.monthAbbr
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ExpenseDetailViewModel(
    private val expenseId: ExpenseId,
    private val expenses: ExpenseRepository,
    private val people: PersonRepository,
    groups: GroupRepository,
    private val comments: CommentRepository,
    private val imagePicker: ImagePicker,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    /** Same-group, same-parent-category spending for one month — the trends card. */
    data class MonthSpend(val label: String, val amount: Money)

    data class State(
        val expense: Expense? = null,
        val comments: List<Comment> = emptyList(),
        val personById: Map<PersonId, Person> = emptyMap(),
        val group: Group? = null,
        val me: PersonId? = null,
        val trends: List<MonthSpend> = emptyList(),
    )

    val state: StateFlow<State> = combine(
        expenses.observe(expenseId),
        comments.observeForExpense(expenseId),
        people.observeAll(),
        groups.observeAll(includeArchived = true),
        expenses.observeAll(),
    ) { e, cs, ppl, gs, all ->
        State(
            expense = e,
            comments = cs,
            personById = ppl.associateBy { it.id },
            group = e?.groupId?.let { id -> gs.firstOrNull { it.id == id } },
            me = ppl.firstOrNull { it.isMe }?.id,
            trends = e?.let { trendsFor(it, all) }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    private fun trendsFor(e: Expense, all: List<Expense>): List<MonthSpend> {
        val parent = parentOf(e.categoryId)
        val slice = all.filter {
            it.groupId == e.groupId && !it.isPayment &&
                it.currency == e.currency && parentOf(it.categoryId) == parent
        }
        return (-1..1).map { offset ->
            val month = e.date.plus(DatePeriod(months = offset))
            val total = slice
                .filter { it.date.year == month.year && it.date.month == month.month }
                .fold(Money.zero(e.currency)) { acc, x -> acc + x.cost }
            MonthSpend(label = monthAbbr(month.monthNumber), amount = total)
        }
    }

    private fun parentOf(id: CategoryId): CategoryId =
        DefaultCategories.get(id).parentId ?: id

    fun addComment(content: String) {
        val text = content.trim()
        val author = state.value.me
        if (text.isEmpty() || author == null) return
        viewModelScope.launch {
            val now = clock.now()
            comments.add(
                Comment(
                    id = CommentId(idGenerator.next()),
                    expenseId = expenseId,
                    authorId = author,
                    content = text,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    fun deleteComment(id: CommentId) {
        viewModelScope.launch { comments.delete(id) }
    }

    fun attachReceipt() {
        viewModelScope.launch {
            val url = imagePicker.pickFromGallery() ?: return@launch
            val e = state.value.expense ?: return@launch
            expenses.save(e.copy(receiptUrl = url, updatedAt = clock.now()))
            e.receiptUrl?.takeIf { it.isLocalReceipt() }?.let(imagePicker::discard)
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            state.value.expense?.receiptUrl?.takeIf { it.isLocalReceipt() }?.let(imagePicker::discard)
            expenses.softDelete(expenseId)
            onDone()
        }
    }
}

@Composable
fun ExpenseDetailScreen(expenseId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val vm: ExpenseDetailViewModel = koinViewModel(parameters = { parametersOf(ExpenseId(expenseId)) })
    val state by vm.state.collectAsStateWithLifecycle()
    val e = state.expense
    val me = state.me
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (e?.isPayment == true) "Delete payment?" else "Delete expense?") },
            text = { Text("This removes it for everyone in the split. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.delete(onBack)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    Scaffold { padding ->
        if (e == null || me == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            DetailHeader(
                expense = e,
                onBack = onBack,
                onAttachReceipt = vm::attachReceipt,
                onDelete = { showDeleteConfirm = true },
                onEdit = onEdit,
            )

            TitleBlock(expense = e, personById = state.personById)

            SplitTree(expense = e, me = me, personById = state.personById)

            e.receiptUrl?.let { url ->
                ListDivider()
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Receipt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = url,
                        contentDescription = "Receipt",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                    )
                }
            }

            if (!e.isPayment && state.trends.any { it.amount.isPositive }) {
                TrendsCard(
                    groupName = state.group?.name,
                    categoryId = e.categoryId,
                    trends = state.trends,
                )
            }

            e.notes?.takeIf { it.isNotBlank() }?.let {
                ListDivider()
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            ListDivider()
            CommentsSection(
                comments = state.comments,
                me = me,
                personById = state.personById,
                onAdd = vm::addComment,
                onDelete = vm::deleteComment,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Category-tinted band with a large faded glyph and the action icons. */
@Composable
private fun DetailHeader(
    expense: Expense,
    onBack: () -> Unit,
    onAttachReceipt: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val tint = if (expense.isPayment) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val onTint = if (expense.isPayment) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(modifier = Modifier.fillMaxWidth().background(tint)) {
        Icon(
            imageVector = if (expense.isPayment) Icons.Outlined.Payments else categoryVectorFor(expense.categoryId),
            contentDescription = null,
            tint = onTint.copy(alpha = 0.25f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(96.dp)
                .offsetForGlyph(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = onTint)
            }
            Spacer(Modifier.weight(1f))
            if (!expense.isPayment) {
                IconButton(onClick = onAttachReceipt) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = "Attach receipt", tint = onTint)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = onTint)
            }
            if (!expense.isPayment) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = onTint)
                }
            }
        }
    }
}

// The glyph peeks out of the band's bottom edge like Splitwise's watermark.
private fun Modifier.offsetForGlyph(): Modifier = this.padding(bottom = 4.dp)

@Composable
private fun TitleBlock(expense: Expense, personById: Map<PersonId, Person>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            expense.description,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            expense.cost.format(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        val zone = TimeZone.currentSystemDefault()
        val addedBy = personById[expense.createdBy]?.displayName ?: "someone"
        Text(
            "Added by $addedBy on ${expense.createdAt.toLocalDateTime(zone).date.formatMedium()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expense.updatedAt != expense.createdAt) {
            Text(
                "Updated on ${expense.updatedAt.toLocalDateTime(zone).date.formatMedium()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expense.repeatInterval != RepeatInterval.NEVER) {
            val next = expense.nextRepeatAt?.toLocalDateTime(zone)?.date?.formatMedium()
            Text(
                "Repeats ${expense.repeatInterval.name.lowercase()}" + (next?.let { " · next on $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Splitwise's payer line with the indented owes-tree and connector lines. */
@Composable
private fun SplitTree(expense: Expense, me: PersonId, personById: Map<PersonId, Person>) {
    val payers = expense.shares.filter { it.paidShare.isPositive }
    val owers = if (expense.isPayment) {
        expense.shares.filter { it.owedShare.isPositive && it.paidShare.isZero }
    } else {
        expense.shares.filter { it.owedShare.isPositive }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        payers.forEach { share ->
            val person = personById[share.personId]
            Row(verticalAlignment = Alignment.CenterVertically) {
                person?.let { PersonAvatar(it, size = 44.dp) }
                Spacer(Modifier.width(16.dp))
                val name = if (share.personId == me) "You" else person?.displayName ?: "Someone"
                Text(
                    "$name paid ${share.paidShare.format()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        owers.forEachIndexed { index, share ->
            val person = personById[share.personId]
            val name = if (share.personId == me) "You" else person?.displayName ?: "Someone"
            val verb = if (share.personId == me) "owe" else "owes"
            val label = if (expense.isPayment) {
                if (share.personId == me) "You received" else "${person?.displayName ?: "Someone"} received"
            } else {
                "$name $verb"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TreeConnector(isLast = index == owers.lastIndex)
                Spacer(Modifier.width(12.dp))
                Text(
                    "$label ${share.owedShare.format()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }
}

/** The └ connector Splitwise draws from the payer avatar to each owed line. */
@Composable
private fun TreeConnector(isLast: Boolean) {
    val color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Canvas(modifier = Modifier.width(44.dp).height(44.dp)) {
        val x = size.width / 2
        val stroke = 1.dp.toPx()
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, if (isLast) size.height / 2 else size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(x, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** "Spending trends for <group> :: <category>" — three months of the same slice. */
@Composable
private fun TrendsCard(
    groupName: String?,
    categoryId: CategoryId,
    trends: List<ExpenseDetailViewModel.MonthSpend>,
) {
    val category = DefaultCategories.get(categoryId)
    val max = trends.maxOf { it.amount.minorUnits }.coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            buildString {
                append("Spending trends")
                groupName?.let { append(" for $it") }
                append(" :: ${category.name}")
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        trends.forEach { month ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                )
                Box(modifier = Modifier.weight(1f).height(20.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(month.amount.minorUnits.toFloat() / max)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    month.amount.format(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CommentsSection(
    comments: List<Comment>,
    me: PersonId,
    personById: Map<PersonId, Person>,
    onAdd: (String) -> Unit,
    onDelete: (CommentId) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Comments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        comments.forEach { comment ->
            CommentRow(
                comment = comment,
                authorName = if (comment.authorId == me) "You" else personById[comment.authorId]?.displayName ?: "Someone",
                isMine = comment.authorId == me,
                onDelete = { onDelete(comment.id) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Add a comment") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                enabled = input.isNotBlank(),
                onClick = {
                    onAdd(input)
                    input = ""
                },
            ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send") }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    authorName: String,
    isMine: Boolean,
    onDelete: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    comment.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date.formatMedium(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(comment.content, style = MaterialTheme.typography.bodyMedium)
        }
        if (isMine) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete comment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
