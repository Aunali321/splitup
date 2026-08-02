package app.splitup.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.repository.PersonRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

class EditProfileViewModel(
    private val people: PersonRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    val me: StateFlow<Person?> = people.observeMe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun save(firstName: String, lastName: String?, email: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            val current = me.value ?: return@launch
            people.save(
                current.copy(
                    firstName = firstName.trim().ifBlank { current.firstName },
                    lastName = lastName?.trim()?.ifBlank { null },
                    email = email?.trim()?.ifBlank { null },
                    updatedAt = clock.now(),
                ),
            )
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val vm: EditProfileViewModel = koinViewModel()
    val me by vm.me.collectAsStateWithLifecycle()
    var first by remember(me) { mutableStateOf(me?.firstName.orEmpty()) }
    var last by remember(me) { mutableStateOf(me?.lastName.orEmpty()) }
    var email by remember(me) { mutableStateOf(me?.email.orEmpty()) }
    val canSave = first.trim().isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit profile", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(
                        enabled = canSave,
                        onClick = { vm.save(first, last, email, onBack) },
                    ) { Icon(Icons.Outlined.Check, contentDescription = "Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = first,
                onValueChange = { first = it },
                label = { Text("First name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = last,
                onValueChange = { last = it },
                label = { Text("Last name (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}
