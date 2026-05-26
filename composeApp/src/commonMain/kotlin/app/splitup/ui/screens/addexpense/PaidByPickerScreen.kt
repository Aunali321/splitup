package app.splitup.ui.screens.addexpense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.PersonId
import app.splitup.ui.components.PersonAvatar
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaidByPickerScreen(
    groupId: String?,
    friendId: String?,
    onBack: () -> Unit,
) {
    val vm: AddExpenseViewModel = koinViewModel()
    LaunchedEffect(groupId, friendId) {
        vm.bindScope(groupId?.let { GroupId(it) }, friendId?.let { PersonId(it) })
    }
    val scope by vm.scope.collectAsStateWithLifecycle()
    val state by vm.draft.state.collectAsStateWithLifecycle()
    val selected = state.payers.keys.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Who paid?", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn {
                items(scope.members, key = { it.id.value }) { person ->
                    val isSelected = person.id == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.draft.setSinglePayer(person.id)
                                onBack()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonAvatar(person)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            if (person.isMe) "${person.displayName} (you)" else person.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}
