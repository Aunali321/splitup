package app.splitup.ui.screens.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.ui.components.LoadingPane
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Free-text scratchpad shared by the whole group, like Splitwise's whiteboard. */
class GroupWhiteboardViewModel(
    groupId: GroupId,
    private val groups: GroupRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    val group = groups.observe(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(text: String, onDone: () -> Unit) {
        val g = group.value ?: return
        viewModelScope.launch {
            groups.save(g.copy(whiteboard = text.trim().ifEmpty { null }, updatedAt = clock.now()))
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupWhiteboardScreen(groupId: String, onBack: () -> Unit) {
    val vm: GroupWhiteboardViewModel = koinViewModel(parameters = { parametersOf(GroupId(groupId)) })
    val group by vm.group.collectAsStateWithLifecycle()
    val g = group ?: return LoadingPane(onBack = onBack, modifier = Modifier.fillMaxSize())

    key(g.id) {
        var text by remember { mutableStateOf(g.whiteboard.orEmpty()) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Whiteboard", fontWeight = FontWeight.SemiBold)
                            Text(
                                g.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.save(text, onBack) }) {
                            Icon(Icons.Outlined.Check, contentDescription = "Save")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "Visible to everyone in the group.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Grocery list, chores, trip plans…") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}
