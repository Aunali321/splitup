package app.splitup.ui.platform

import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication

class IosAppLock : AppLock {
    override val isAvailable: Boolean
        get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    override fun authenticate(onResult: (Boolean) -> Unit) {
        LAContext().evaluatePolicy(
            LAPolicyDeviceOwnerAuthentication,
            localizedReason = "Unlock SplitUp",
        ) { success, _ -> onResult(success) }
    }
}
