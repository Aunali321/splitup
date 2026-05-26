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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.GroupMember
import app.splitup.shared.domain.model.GroupRole
import app.splitup.shared.domain.model.GroupType
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.util.IdGenerator
import app.splitup.ui.components.FormField
import app.splitup.ui.components.SimpleFormDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.compose.viewmodel.koinViewModel

class GroupsViewModel(
    private val groups: GroupRepository,
    private val people: PersonRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    val state: StateFlow<List<Group>> = groups.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createGroup(name: String, onCreated: (GroupId) -> Unit = {}) {
        viewModelScope.launch {
            val me = people.observeAll().first().firstOrNull { it.isMe } ?: return@launch
            val now = clock.now()
            val id = GroupId(idGenerator.next())
            groups.save(
                Group(
                    id = id,
                    name = name,
                    type = GroupType.OTHER,
                    members = listOf(GroupMember(me.id, GroupRole.OWNER, now)),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            onCreated(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onOpenGroup: (GroupId) -> Unit) {
    val vm: GroupsViewModel = koinViewModel()
    val groups by vm.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        SimpleFormDialog(
            title = "New group",
            primaryLabel = "Create",
            fields = listOf(FormField("name", "Group name", required = true)),
            onSubmit = { values ->
                vm.createGroup(values.getValue("name"))
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Groups", fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Create group") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (groups.isEmpty()) {
                EmptyGroups()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(groups, key = { it.id.value }) { group ->
                        GroupRow(group = group, onClick = { onOpenGroup(group.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(group: Group, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = groupTint(group.name)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = groupIcon(group.type),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${group.members.size} member${if (group.members.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val groupTints = listOf(
    Color(0xFFE65100), Color(0xFF1B7F44), Color(0xFF7A4F9C),
    Color(0xFFC2185B), Color(0xFF1976D2), Color(0xFF00897B),
    Color(0xFFD84315), Color(0xFF455A64),
)

private fun groupTint(name: String): Color {
    var h = 0
    for (c in name) h = 31 * h + c.code
    return groupTints[(h.rem(groupTints.size) + groupTints.size).rem(groupTints.size)]
}

@Composable
private fun EmptyGroups() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("No groups yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Create a group for a household, trip, or anything you split regularly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun groupIcon(type: GroupType): ImageVector = when (type) {
    GroupType.HOME, GroupType.APARTMENT, GroupType.HOUSE -> Icons.Rounded.Home
    GroupType.TRIP -> Icons.Rounded.Flight
    else -> Icons.Outlined.Groups
}
