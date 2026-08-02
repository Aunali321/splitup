package app.splitup.ui.screens.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.debt.DebtSimplifier
import app.splitup.shared.domain.export.GroupCsv
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.GroupMember
import app.splitup.shared.domain.model.GroupRole
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.foundation.layout.height
import app.splitup.shared.data.sync.InviteResult
import app.splitup.shared.data.sync.SyncService
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.split.SplitStrategy
import app.splitup.ui.components.ListDivider
import app.splitup.ui.components.PersonAvatar
import app.splitup.ui.components.SectionLabel
import app.splitup.ui.components.SettingRow
import app.splitup.ui.screens.addexpense.AddExpenseDraft
import app.splitup.ui.platform.FileSharer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class GroupSettingsViewModel(
    private val groupId: GroupId,
    private val groups: GroupRepository,
    private val people: PersonRepository,
    private val expenses: ExpenseRepository,
    private val fileSharer: FileSharer,
    private val sync: SyncService,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    data class State(
        val group: Group? = null,
        val members: List<Person> = emptyList(),
        /** Friends not yet in the group. */
        val addable: List<Person> = emptyList(),
        /** Members with a non-zero net in this group cannot be removed. */
        val removableIds: Set<PersonId> = emptySet(),
    )

    val state: StateFlow<State> = combine(
        groups.observe(groupId),
        people.observeAll(),
        expenses.observeInGroup(groupId),
    ) { g, ppl, exps ->
        if (g == null) State()
        else {
            val memberIds = g.members.map { it.personId }.toSet()
            State(
                group = g,
                members = g.members.mapNotNull { m -> ppl.firstOrNull { it.id == m.personId } },
                addable = ppl.filter { it.id !in memberIds },
                removableIds = memberIds.filterTo(mutableSetOf()) { DebtSimplifier.netOf(exps, it).isEmpty() },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    fun rename(newName: String) = viewModelScope.launch {
        val g = state.value.group ?: return@launch
        groups.save(g.copy(name = newName, updatedAt = clock.now()))
    }

    private val _inviteResult = MutableStateFlow<InviteResult?>(null)
    val inviteResult: StateFlow<InviteResult?> = _inviteResult.asStateFlow()

    fun invite(email: String) = viewModelScope.launch {
        _inviteResult.value = sync.invite(groupId.value, email)
    }

    fun clearInviteResult() { _inviteResult.value = null }

    fun addMember(person: PersonId) = viewModelScope.launch {
        val g = state.value.group ?: return@launch
        if (g.members.any { it.personId == person }) return@launch
        groups.save(
            g.copy(
                members = g.members + GroupMember(person, GroupRole.MEMBER, clock.now()),
                updatedAt = clock.now(),
            ),
        )
    }

    /** Callers gate on [State.removableIds] — outstanding balances block removal. */
    fun removeMember(person: PersonId) = viewModelScope.launch {
        val g = state.value.group ?: return@launch
        groups.save(
            g.copy(
                members = g.members.filterNot { it.personId == person },
                updatedAt = clock.now(),
            ),
        )
    }

    fun setSimplifyDebts(enabled: Boolean) = viewModelScope.launch {
        val g = state.value.group ?: return@launch
        groups.save(g.copy(simplifyByDefault = enabled, updatedAt = clock.now()))
    }

    fun setDefaultSplit(strategy: SplitStrategy?) = viewModelScope.launch {
        val g = state.value.group ?: return@launch
        groups.save(g.copy(defaultSplit = strategy, updatedAt = clock.now()))
    }

    fun export() = viewModelScope.launch {
        val g = state.value.group ?: return@launch
        val csv = GroupCsv.build(expenses.observeInGroup(groupId).first(), state.value.members)
        val slug = g.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "group" }
        fileSharer.shareTextFile("$slug-export.csv", "text/csv", csv)
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        groups.delete(groupId)
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(groupId: String, onBack: () -> Unit, onDeleted: () -> Unit) {
    val vm: GroupSettingsViewModel = koinViewModel(parameters = { parametersOf(GroupId(groupId)) })
    val state by vm.state.collectAsStateWithLifecycle()
    val group = state.group
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddMembers by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    val inviteResult by vm.inviteResult.collectAsStateWithLifecycle()
    var showDefaultSplit by remember { mutableStateOf(false) }
    var blockedRemoval by remember { mutableStateOf<Person?>(null) }

    if (showDefaultSplit && group != null) {
        DefaultSplitDialog(
            members = state.members,
            current = group.defaultSplit,
            onSave = { vm.setDefaultSplit(it); showDefaultSplit = false },
            onDismiss = { showDefaultSplit = false },
        )
    }

    if (showInvite) {
        InviteDialog(
            result = inviteResult,
            onInvite = vm::invite,
            onDismiss = { showInvite = false; vm.clearInviteResult() },
        )
    }

    if (showAddMembers) {
        AddMembersSheet(
            addable = state.addable,
            onAdd = vm::addMember,
            onDismiss = { showAddMembers = false },
        )
    }

    blockedRemoval?.let { person ->
        AlertDialog(
            onDismissRequest = { blockedRemoval = null },
            title = { Text("Can't remove ${person.firstName}") },
            text = { Text("${person.displayName} has outstanding balances in this group. Settle up first, then remove them.") },
            confirmButton = { TextButton(onClick = { blockedRemoval = null }) { Text("OK") } },
        )
    }

    if (showRename && group != null) {
        var name by remember(group) { mutableStateOf(group.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename group") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.rename(name.trim()); showRename = false },
                    enabled = name.trim().isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete group?") },
            text = { Text("This removes the group and every expense in it. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.delete(onDeleted)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (group == null) return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${group.members.size} ${if (group.members.size == 1) "member" else "members"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ListDivider()

            SectionLabel("Group")
            SettingRow(
                icon = Icons.Outlined.EditNote,
                title = "Rename group",
                onClick = { showRename = true },
            )
            SettingRow(
                icon = Icons.Outlined.CallSplit,
                title = "Default split",
                onClick = { showDefaultSplit = true },
            )
            SettingRow(
                icon = Icons.Outlined.FileDownload,
                title = "Export as spreadsheet (CSV)",
                onClick = vm::export,
            )
            SettingRow(
                icon = Icons.Outlined.AccountTree,
                title = "Simplify group debts",
                subtitle = "Combine debts to reduce the number of repayments between members.",
                trailing = {
                    Switch(
                        checked = group.simplifyByDefault,
                        onCheckedChange = vm::setSimplifyDebts,
                    )
                },
                onClick = { vm.setSimplifyDebts(!group.simplifyByDefault) },
            )
            SettingRow(
                icon = Icons.Outlined.DeleteForever,
                title = "Delete group",
                titleColor = MaterialTheme.colorScheme.error,
                iconTint = MaterialTheme.colorScheme.error,
                onClick = { showDeleteConfirm = true },
            )

            ListDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionLabel("${state.members.size} ${if (state.members.size == 1) "member" else "members"}")
            SettingRow(
                icon = Icons.Outlined.PersonAdd,
                title = "Add members",
                onClick = { showAddMembers = true },
            )
            SettingRow(
                icon = Icons.Outlined.Email,
                title = "Invite by email",
                subtitle = "Shares this group with them once they sign in",
                onClick = { showInvite = true },
            )
            state.members.forEach { person ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(person, size = 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (person.isMe) "${person.displayName} (you)" else person.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        person.email?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(
                        onClick = {
                            if (person.id in state.removableIds) vm.removeMember(person.id)
                            else blockedRemoval = person
                        },
                    ) {
                        Icon(
                            Icons.Outlined.PersonRemove,
                            contentDescription = "Remove ${person.displayName}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

private enum class DefaultSplitMode(val label: String) {
    EQUAL("Equally"),
    PERCENT("By percentages"),
    SHARES("By shares"),
}

/** Splitwise's per-group default split: pre-selects the split on new expenses. */
@Composable
private fun DefaultSplitDialog(
    members: List<Person>,
    current: SplitStrategy?,
    onSave: (SplitStrategy?) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember {
        mutableStateOf(
            when (current) {
                is SplitStrategy.Percent -> DefaultSplitMode.PERCENT
                is SplitStrategy.Shares -> DefaultSplitMode.SHARES
                else -> DefaultSplitMode.EQUAL
            },
        )
    }
    val inputs = remember {
        mutableStateMapOf<PersonId, String>().apply {
            when (current) {
                is SplitStrategy.Percent ->
                    current.basisPoints.forEach { (id, bp) -> put(id, AddExpenseDraft.formatPercent(bp)) }
                is SplitStrategy.Shares ->
                    current.shares.forEach { (id, n) -> put(id, n.toString()) }
                else -> {}
            }
        }
    }

    val percentSum = members.sumOf { AddExpenseDraft.parsePercent(inputs[it.id]) }
    val shareCounts = members.associate { it.id to (inputs[it.id]?.trim()?.toIntOrNull() ?: 0) }
    val canSave = when (mode) {
        DefaultSplitMode.EQUAL -> true
        DefaultSplitMode.PERCENT -> percentSum == AddExpenseDraft.TOTAL_BP
        DefaultSplitMode.SHARES -> shareCounts.values.any { it > 0 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default split") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Applies to new expenses you add to this group.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DefaultSplitMode.entries.forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { mode = m },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Text(m.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (mode != DefaultSplitMode.EQUAL) {
                    members.forEach { person ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (person.isMe) "${person.displayName} (you)" else person.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = inputs[person.id].orEmpty(),
                                onValueChange = { inputs[person.id] = it },
                                modifier = Modifier.width(96.dp),
                                singleLine = true,
                                placeholder = { Text("0") },
                                suffix = { Text(if (mode == DefaultSplitMode.PERCENT) "%" else "×") },
                            )
                        }
                    }
                    if (mode == DefaultSplitMode.PERCENT && percentSum != AddExpenseDraft.TOTAL_BP) {
                        Text(
                            "Percentages must add up to 100% (now ${AddExpenseDraft.formatPercent(percentSum)}%).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        when (mode) {
                            DefaultSplitMode.EQUAL -> null
                            DefaultSplitMode.PERCENT -> SplitStrategy.Percent(
                                members.associate { it.id to AddExpenseDraft.parsePercent(inputs[it.id]) }
                                    .filterValues { it > 0 },
                            )
                            DefaultSplitMode.SHARES -> SplitStrategy.Shares(
                                shareCounts.filterValues { it > 0 },
                            )
                        },
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMembersSheet(
    addable: List<Person>,
    onAdd: (PersonId) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Add members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (addable.isEmpty()) {
                Text(
                    "All of your friends are already in this group. Add new friends from the Friends tab.",
                    modifier = Modifier.padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(addable, key = { it.id.value }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(person.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PersonAvatar(person, size = 40.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(person.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}



@Composable
private fun InviteDialog(
    result: InviteResult?,
    onInvite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite by email") },
        text = {
            Column {
                Text(
                    "They will see this group and its expenses. Sharing needs a connection, " +
                        "so this only works while you are signed in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                result?.let {
                    Spacer(Modifier.height(8.dp))
                    when (it) {
                        is InviteResult.Invited -> InvitedMessage(it.claimToken)
                        InviteResult.NotSignedIn -> ErrorMessage("Sign in first, from Account > Sync.")
                        InviteResult.NotAMember -> ErrorMessage("You are not a member of this group.")
                        is InviteResult.Failed -> ErrorMessage(it.message)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onInvite(email.trim()) }, enabled = email.contains("@")) {
                Text("Invite")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun InvitedMessage(claimToken: String?) {
    if (claimToken == null) {
        Text(
            "Invited — the group is shared with them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }
    Text(
        "Invited. They don't have an account yet — send them this invite code to enter when they sign in:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    SelectionContainer {
        Text(claimToken, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
