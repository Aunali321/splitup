package app.splitup.ui.screens.addexpense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.ui.components.GroupAvatar
import app.splitup.ui.components.PersonAvatar
import app.splitup.ui.components.SectionLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

class ExpenseWithViewModel(
    groups: GroupRepository,
    people: PersonRepository,
) : ViewModel() {
    data class State(val groups: List<Group> = emptyList(), val friends: List<Person> = emptyList())

    val state: StateFlow<State> = combine(groups.observeAll(), people.observeFriends()) { gs, fs ->
        State(groups = gs, friends = fs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())
}

/**
 * Splitwise's "With you and:" step for expenses started from a tab: pick a
 * whole group (one tap) or any set of friends (multi-select, then confirm).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseWithScreen(
    onBack: () -> Unit,
    onPickGroup: (GroupId) -> Unit,
    onPickFriends: (List<PersonId>) -> Unit,
) {
    val vm: ExpenseWithViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<PersonId>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add expense", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(
                        enabled = selected.isNotEmpty(),
                        onClick = { onPickFriends(selected.toList()) },
                    ) { Icon(Icons.Outlined.Check, contentDescription = "Continue") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "with") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("With ", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("you ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("and:", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.groups.isNotEmpty()) {
                item(key = "groups-header") { SectionLabel("Groups") }
                items(state.groups, key = { "g-${it.id.value}" }) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickGroup(group.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupAvatar(group, size = 44.dp, corner = 12.dp)
                        Spacer(Modifier.width(16.dp))
                        Text("All of ${group.name}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (state.friends.isNotEmpty()) {
                item(key = "friends-header") { SectionLabel("Friends") }
                items(state.friends, key = { "f-${it.id.value}" }) { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (friend.id in selected) selected - friend.id else selected + friend.id
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonAvatar(friend, size = 44.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(friend.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = friend.id in selected,
                            onCheckedChange = {
                                selected = if (friend.id in selected) selected - friend.id else selected + friend.id
                            },
                        )
                    }
                }
            }
        }
    }
}
