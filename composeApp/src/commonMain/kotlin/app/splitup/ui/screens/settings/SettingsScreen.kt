package app.splitup.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.data.repository.LocalDataReset
import app.splitup.shared.data.repository.UserPreferencesRepository
import app.splitup.shared.data.sync.SyncService
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.ThemePreference
import app.splitup.shared.domain.model.UserPreferences
import app.splitup.ui.components.CurrencyPicker
import app.splitup.ui.components.SectionLabel
import app.splitup.ui.components.SettingRow
import app.splitup.ui.platform.AppLock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

class SettingsViewModel(
    private val prefsRepo: UserPreferencesRepository,
    private val reset: LocalDataReset,
    private val sync: SyncService,
    val appLock: AppLock,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepo.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    fun setHomeCurrency(currency: Currency) = viewModelScope.launch {
        prefsRepo.update { it.copy(homeCurrency = currency) }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch {
        prefsRepo.update { it.copy(theme = theme) }
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.update { it.copy(useDynamicColor = enabled) }
    }

    /** Enabling requires a successful device auth first, so users can't lock themselves out. */
    fun setAppLock(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch { prefsRepo.update { it.copy(biometricLock = false) } }
            return
        }
        appLock.authenticate { success ->
            if (success) {
                viewModelScope.launch { prefsRepo.update { it.copy(biometricLock = true) } }
            }
        }
    }

    /** Erasing is local, so any live sync session ends first — nothing propagates. */
    fun resetAllData() = viewModelScope.launch {
        sync.signOut()
        reset.clearAll()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = koinViewModel()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showCurrencyPicker) {
        CurrencyPicker(
            onPick = { vm.setHomeCurrency(it); showCurrencyPicker = false },
            onDismiss = { showCurrencyPicker = false },
        )
    }
    if (showThemePicker) {
        ThemeDialog(
            current = prefs.theme,
            onPick = { vm.setTheme(it); showThemePicker = false },
            onDismiss = { showThemePicker = false },
        )
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Erase all data?") },
            text = {
                Text("Removes all expenses, groups, friends and preferences on this device. Cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAllData()
                    showResetConfirm = false
                }) { Text("Erase", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionLabel("Preferences")
            SettingRow(
                icon = Icons.Outlined.CurrencyExchange,
                title = "Default currency",
                trailing = { Text("${prefs.homeCurrency.code}  ${prefs.homeCurrency.symbol}", style = MaterialTheme.typography.labelLarge) },
                onClick = { showCurrencyPicker = true },
            )
            SettingRow(
                icon = themeIcon(prefs.theme),
                title = "Theme",
                trailing = { Text(themeLabel(prefs.theme), style = MaterialTheme.typography.labelLarge) },
                onClick = { showThemePicker = true },
            )
            SettingRow(
                icon = Icons.Outlined.Palette,
                title = "Dynamic colour",
                subtitle = "Tints the app to your wallpaper",
                trailing = {
                    Switch(checked = prefs.useDynamicColor, onCheckedChange = vm::setDynamicColor)
                },
                onClick = { vm.setDynamicColor(!prefs.useDynamicColor) },
            )
            if (vm.appLock.isAvailable) {
                SettingRow(
                    icon = Icons.Outlined.Lock,
                    title = "App lock",
                    subtitle = "Require biometrics or device credential on launch",
                    trailing = {
                        Switch(checked = prefs.biometricLock, onCheckedChange = vm::setAppLock)
                    },
                    onClick = { vm.setAppLock(!prefs.biometricLock) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionLabel("Data")
            SettingRow(
                icon = Icons.Outlined.DeleteForever,
                title = "Erase all data",
                subtitle = "Removes every group, expense and friend on this device",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showResetConfirm = true },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionLabel("About")
            SettingRow(
                icon = Icons.Outlined.Info,
                title = "SplitUp!",
                subtitle = "Open source · v0.1.0",
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}



@Composable
private fun ThemeDialog(current: ThemePreference, onPick: (ThemePreference) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemePreference.entries.forEach { theme ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(theme) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == theme, onClick = { onPick(theme) })
                        Spacer(Modifier.width(8.dp))
                        Text(themeLabel(theme), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun themeIcon(theme: ThemePreference): ImageVector = when (theme) {
    ThemePreference.LIGHT -> Icons.Outlined.LightMode
    ThemePreference.DARK -> Icons.Outlined.DarkMode
    ThemePreference.SYSTEM -> Icons.Outlined.AutoMode
}

private fun themeLabel(theme: ThemePreference): String = when (theme) {
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
    ThemePreference.SYSTEM -> "System"
}
