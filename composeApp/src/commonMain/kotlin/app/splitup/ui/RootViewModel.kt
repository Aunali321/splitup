package app.splitup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitup.shared.data.repository.UserPreferencesRepository
import app.splitup.shared.domain.model.UserPreferences
import app.splitup.shared.data.sync.SyncService
import app.splitup.shared.domain.usecase.MaterializeRecurringExpensesUseCase
import app.splitup.ui.platform.AppLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RootViewModel(
    private val prefsRepo: UserPreferencesRepository,
    private val appLock: AppLock,
    materializeRecurring: MaterializeRecurringExpensesUseCase,
    sync: SyncService,
) : ViewModel() {
    /**
     * Null until Room's first emission — the shell renders a blank themed frame
     * for that instant instead of flashing onboarding at returning users.
     */
    val preferences: StateFlow<UserPreferences?> = prefsRepo.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Null until the app-lock preference is read; true blocks the UI until unlocked. */
    private val _locked = MutableStateFlow<Boolean?>(null)
    val locked: StateFlow<Boolean?> = _locked.asStateFlow()

    init {
        viewModelScope.launch {
            _locked.value = appLock.isAvailable && prefsRepo.get().biometricLock
        }
        viewModelScope.launch { materializeRecurring() }
        // No-op unless a previous session was signed in.
        viewModelScope.launch { runCatching { sync.start() } }
    }

    fun unlock() {
        appLock.authenticate { success ->
            if (success) _locked.value = false
        }
    }

    /** Re-arms the lock whenever the app leaves the foreground. */
    fun relock() {
        viewModelScope.launch {
            if (appLock.isAvailable && prefsRepo.get().biometricLock) _locked.value = true
        }
    }
}
