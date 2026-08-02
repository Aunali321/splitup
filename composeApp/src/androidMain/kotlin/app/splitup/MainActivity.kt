package app.splitup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.splitup.ui.App
import app.splitup.ui.oauth.OAuthCallbackBus
import app.splitup.ui.platform.AndroidAppLock
import app.splitup.ui.platform.AndroidImagePicker
import app.splitup.ui.platform.AppLock
import app.splitup.ui.platform.ImagePicker
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

// FragmentActivity (not ComponentActivity) because BiometricPrompt requires one.
class MainActivity : FragmentActivity() {
    private val oauthBus: OAuthCallbackBus by inject()
    private val imagePicker: ImagePicker by inject()
    private val appLock: AppLock by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (imagePicker as AndroidImagePicker).attach(this)
        (appLock as AndroidAppLock).attach(this)
        enableEdgeToEdge()
        setContent { App() }
        intent?.let(::handleIntent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        (imagePicker as AndroidImagePicker).detach()
        (appLock as AndroidAppLock).detach()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        val uri: Uri = intent.data ?: return
        if (uri.scheme != "splitup" || uri.host != "oauth") return
        // splitup://oauth/<provider>?code=...&state=... or ?error=...
        val provider = uri.pathSegments.firstOrNull().orEmpty()
        if (provider.isBlank()) return
        val payload = OAuthCallbackBus.Payload(
            provider = provider,
            code = uri.getQueryParameter("code"),
            state = uri.getQueryParameter("state"),
            error = uri.getQueryParameter("error"),
        )
        lifecycleScope.launch { oauthBus.publish(payload) }
    }
}
