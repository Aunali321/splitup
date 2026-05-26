package app.splitup.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitup.shared.data.repository.UserPreferencesRepository
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class OnboardingViewModel(
    private val prefsRepo: UserPreferencesRepository,
) : ViewModel() {
    private val _selectedCurrency = MutableStateFlow(Currency.DEFAULT)
    val selectedCurrency: StateFlow<Currency> = _selectedCurrency.asStateFlow()

    fun select(currency: Currency) = _selectedCurrency.update { currency }

    fun saveCurrency(onDone: () -> Unit) {
        viewModelScope.launch {
            val existing = prefsRepo.get() ?: UserPreferences()
            prefsRepo.save(existing.copy(homeCurrency = _selectedCurrency.value))
            onDone()
        }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            val existing = prefsRepo.get() ?: UserPreferences()
            prefsRepo.save(existing.copy(onboardingCompletedAt = kotlinx.datetime.Clock.System.now()))
            onDone()
        }
    }
}

@Composable
fun OnboardingCurrencyScreen(onContinue: () -> Unit) {
    val vm: OnboardingViewModel = koinViewModel()
    val selected by vm.selectedCurrency.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val all = remember(query) {
        if (query.isBlank()) Currency.ALL
        else Currency.ALL.filter {
            it.code.contains(query, ignoreCase = true) || it.displayName.contains(query, ignoreCase = true)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Your currency", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Used for new expenses by default. You can switch per-expense and use multiple currencies in the same group.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search currency") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(all, key = { it.code }) { currency ->
                Surface(
                    onClick = { vm.select(currency) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == currency, onClick = { vm.select(currency) })
                        Spacer(Modifier.fillMaxWidth(0.04f))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${currency.symbol}  ${currency.displayName}", style = MaterialTheme.typography.titleSmall)
                            Text(currency.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.saveCurrency(onContinue) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
    }
}

