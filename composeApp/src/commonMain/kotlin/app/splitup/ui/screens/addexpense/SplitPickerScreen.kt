package app.splitup.ui.screens.addexpense

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.PersonId
import app.splitup.ui.components.PersonAvatar
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPickerScreen(
    groupId: String?,
    friendId: String?,
    onBack: () -> Unit,
) {
    val vm: AddExpenseViewModel = koinInject()
    LaunchedEffect(groupId, friendId) {
        vm.start(groupId?.let { GroupId(it) }, friendId?.let { PersonId(it) })
    }
    val scope by vm.scope.collectAsStateWithLifecycle()
    val draftReady by vm.draftReady.collectAsStateWithLifecycle()
    if (!draftReady) return
    val state by vm.draft.state.collectAsStateWithLifecycle()
    val mode = state.strategy
    val perPerson = remember(state.amount, state.participants.size, state.currency) {
        val n = state.participants.size
        if (n == 0 || state.amount.isBlank()) Money.zero(state.currency)
        else runCatching {
            val total = Money.parse(state.amount, state.currency)
            Money.ofMinor(total.minorUnits / n, state.currency)
        }.getOrDefault(Money.zero(state.currency))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adjust split", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.Check, contentDescription = "Save") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(
                selectedTabIndex = mode.ordinal,
                edgePadding = 16.dp,
            ) {
                AddExpenseDraft.SplitMode.entries.forEach { m ->
                    Tab(
                        selected = mode == m,
                        onClick = { vm.draft.setStrategy(m) },
                        text = {
                            Text(
                                when (m) {
                                    AddExpenseDraft.SplitMode.Equal -> "Equally"
                                    AddExpenseDraft.SplitMode.Unequally -> "Unequally"
                                    AddExpenseDraft.SplitMode.Percent -> "By percentages"
                                    AddExpenseDraft.SplitMode.Shares -> "By shares"
                                },
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
            // Only the Equally tab is fully wired today — the others select all
            // participants and fall back to equal in buildStrategy. Improving
            // them is a follow-up; the picker captures the user's intent so
            // we can hydrate per-mode inputs later without changing nav.
            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(scope.members, key = { it.id.value }) { person ->
                        val included = person.id in state.participants
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.draft.toggleParticipant(person.id) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PersonAvatar(person)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                if (person.isMe) "${person.displayName} (you)" else person.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Checkbox(checked = included, onCheckedChange = { vm.draft.toggleParticipant(person.id) })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "${perPerson.format()}/person",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${state.participants.size} ${if (state.participants.size == 1) "person" else "people"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
