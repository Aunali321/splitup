package app.splitup.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.util.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

class OnboardingProfileViewModel(
    private val people: PersonRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /**
     * Upserts the single `isMe` row — navigating back and continuing again (or a
     * double tap) updates the same person instead of minting a duplicate "me".
     */
    fun save(firstName: String, lastName: String?, email: String?, onDone: () -> Unit) {
        if (!_saving.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val now = clock.now()
                val existing = people.getMe()
                people.save(
                    Person(
                        id = existing?.id ?: PersonId(idGenerator.next()),
                        firstName = firstName.trim().ifBlank { "Me" },
                        lastName = lastName?.trim()?.ifBlank { null },
                        email = email?.trim()?.ifBlank { null },
                        isMe = true,
                        isRegistered = true,
                        updatedAt = now,
                    )
                )
                onDone()
            } finally {
                _saving.value = false
            }
        }
    }
}

@Composable
fun OnboardingProfileScreen(onContinue: () -> Unit) {
    val vm: OnboardingProfileViewModel = koinViewModel()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("About you", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Used to display your name on expenses. Email is optional — only needed if you'll add others by email.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last name (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { vm.save(firstName, lastName, email, onContinue) },
            enabled = firstName.trim().isNotBlank() && !saving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
    }
}
