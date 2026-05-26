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
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.util.IdGenerator
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.compose.viewmodel.koinViewModel

class OnboardingProfileViewModel(
    private val people: PersonRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    fun save(firstName: String, lastName: String?, email: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            val now = clock.now()
            people.save(
                Person(
                    id = PersonId(idGenerator.next()),
                    firstName = firstName.trim().ifBlank { "Me" },
                    lastName = lastName?.trim()?.ifBlank { null },
                    email = email?.trim()?.ifBlank { null },
                    isMe = true,
                    isRegistered = true,
                    updatedAt = now,
                )
            )
            onDone()
        }
    }
}

@Composable
fun OnboardingProfileScreen(onContinue: () -> Unit) {
    val vm: OnboardingProfileViewModel = koinViewModel()
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
            enabled = firstName.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
    }
}
