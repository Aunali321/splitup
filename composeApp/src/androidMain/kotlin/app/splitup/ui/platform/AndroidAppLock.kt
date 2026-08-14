package app.splitup.ui.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AndroidAppLock(private val context: Context) : AppLock {

    private var activity: FragmentActivity? = null

    fun attach(activity: FragmentActivity) {
        this.activity = activity
    }

    /**
     * On recreation the incoming activity attaches before the outgoing one is
     * destroyed, so only the activity that currently holds the slot may clear it.
     */
    fun detach(activity: FragmentActivity) {
        if (this.activity === activity) this.activity = null
    }

    override val isAvailable: Boolean
        get() = BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    override fun authenticate(onResult: (Boolean) -> Unit) {
        val host = activity ?: return onResult(false)
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    onResult(false)
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock SplitUp")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}
