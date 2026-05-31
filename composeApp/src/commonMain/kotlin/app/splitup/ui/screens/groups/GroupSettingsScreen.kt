package app.splitup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.GroupMember
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.PersonAvatar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class GroupSettingsViewModel(
    private val groupId: GroupId,
    private val groups: GroupRepository,
    private val people: PersonRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    data class State(val group: Group? = null, val members: List<Person> = emptyList())

    val state: StateFlow<State> = combine(groups.observe(groupId), people.observeAll()) { g, ppl ->
        if (g == null) State()
        else State(g, g.members.mapNotNull { m -> ppl.firstOrNull { it.id == m.personId } })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    fun rename(newName: String) = viewModelScope.launch {
        val g = state.value.group ?: return@launch
        groups.save(g.copy(name = newName, updatedAt = clock.now()))
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            SectionLabel("Group")
            ActionRow(
                icon = Icons.Outlined.EditNote,
                title = "Rename group",
                onClick = { showRename = true },
            )
            ActionRow(
                icon = Icons.Outlined.DeleteForever,
                title = "Delete group",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showDeleteConfirm = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            SectionLabel("${state.members.size} members")
            state.members.forEach { person ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(person, size = 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (person.isMe) "${person.displayName} (you)" else person.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        person.email?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
    }
}
