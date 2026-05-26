package app.splitup.shared.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val homeCurrency: Currency = Currency.DEFAULT,
    val convertToHomeInUi: Boolean = true,
    val fxSource: FxSource = FxSource.ECB,
    val locale: String? = null,
    val firstDayOfWeek: Int = 1,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColor: Boolean = true,
    val decimalSeparator: Char? = null,
    val biometricLock: Boolean = false,
    val pushEnabled: Boolean = true,
    /** Null until onboarding finishes. The app shell uses this to gate the main UI. */
    val onboardingCompletedAt: Instant? = null,
) {
    val needsOnboarding: Boolean get() = onboardingCompletedAt == null
}

@Serializable
enum class FxSource { ECB, OPEN_EXCHANGE_RATES, MANUAL, NONE }

@Serializable
enum class ThemePreference { LIGHT, DARK, SYSTEM }
