package app.splitup.ui.platform

/** Desktops have no biometric/credential prompt API worth bridging; never locks. */
class DesktopAppLock : AppLock {
    override val isAvailable = false
    override fun authenticate(onResult: (Boolean) -> Unit) = onResult(false)
}
