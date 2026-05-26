package app.splitup.ui.screens.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.util.IdGenerator
import app.splitup.ui.components.FormField
import app.splitup.ui.components.PersonAvatar
import app.splitup.ui.components.SimpleFormDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.compose.viewmodel.koinViewModel

class FriendsViewModel(
    private val people: PersonRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    val friends: StateFlow<List<Person>> = people.observeFriends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addFriend(firstName: String, lastName: String?, email: String?) {
        viewModelScope.launch {
            people.save(
                Person(
                    id = PersonId(idGenerator.next()),
                    firstName = firstName,
                    lastName = lastName?.ifBlank { null },
                    email = email?.ifBlank { null },
                    isMe = false,
                    isRegistered = false,
                    updatedAt = clock.now(),
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(onOpenFriend: (PersonId) -> Unit) {
    val vm: FriendsViewModel = koinViewModel()
    val friends by vm.friends.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) {
        SimpleFormDialog(
            title = "Add friend",
            primaryLabel = "Add",
            fields = listOf(
                FormField("first", "First name", required = true),
                FormField("last", "Last name"),
                FormField("email", "Email"),
            ),
            onSubmit = { values ->
                vm.addFriend(values.getValue("first"), values["last"], values["email"])
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Friends", fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                text = { Text("Add friend") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (friends.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No friends yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        "Add someone by name or import from Splitwise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(friends, key = { it.id.value }) { friend ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenFriend(friend.id) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PersonAvatar(friend)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.displayName, style = MaterialTheme.typography.titleMedium)
                                friend.email?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}
