package app.splitup.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.ui.screens.importer.SplitwiseImportViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingImportScreen() {
    val onboardingVm: OnboardingViewModel = koinViewModel()
    val importVm: SplitwiseImportViewModel = koinViewModel()
    val status by importVm.status.collectAsStateWithLifecycle()

    // Auto-finish onboarding once import succeeds — user lands in the main app.
    LaunchedEffect(status) {
        if (status is SplitwiseImportViewModel.Status.Done) {
            onboardingVm.completeOnboarding {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Coming from Splitwise?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Import your groups, friends, and expenses in one step. Your Splitwise account stays untouched.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))

        when (val s = status) {
            SplitwiseImportViewModel.Status.Idle -> {
                Button(
                    onClick = importVm::startOAuthFlow,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import from Splitwise") }
                OutlinedButton(
                    onClick = { onboardingVm.completeOnboarding {} },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start fresh") }
                TextButton(onClick = { onboardingVm.completeOnboarding {} }) { Text("I'll do this later") }
            }
            SplitwiseImportViewModel.Status.AwaitingBrowser -> {
                Text("Finish signing in in your browser…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { onboardingVm.completeOnboarding {} }) { Text("Cancel and skip import") }
            }
            is SplitwiseImportViewModel.Status.Running -> {
                Text(s.phase, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
            }
            is SplitwiseImportViewModel.Status.Done -> {
                Text(
                    "Imported ${s.people} people, ${s.groups} groups, ${s.expenses} expenses.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("Finishing up…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is SplitwiseImportViewModel.Status.Failed -> {
                Text("Import failed: ${s.reason}", color = MaterialTheme.colorScheme.error)
                Button(onClick = importVm::startOAuthFlow, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
                TextButton(onClick = { onboardingVm.completeOnboarding {} }) { Text("Skip and continue") }
            }
        }
    }
}
