package app.splitup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitup.shared.data.repository.UserPreferencesRepository
import app.splitup.shared.domain.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RootViewModel(
    private val prefsRepo: UserPreferencesRepository,
) : ViewModel() {
    /**
     * Always emits a value. On first launch the repo synthesises defaults so
     * the UI can route to onboarding immediately, without a perpetual splash.
     */
    val preferences: StateFlow<UserPreferences> = prefsRepo.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
}
