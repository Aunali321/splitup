package app.splitup.ui.platform

/**
 * Per-target device authentication for the app-lock preference. Unavailable
 * platforms never lock and hide the setting.
 */
interface AppLock {
    val isAvailable: Boolean
    fun authenticate(onResult: (Boolean) -> Unit)
}
