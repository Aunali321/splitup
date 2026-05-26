package app.splitup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.splitup.ui.App
import app.splitup.ui.oauth.OAuthCallbackBus
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val oauthBus: OAuthCallbackBus by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
        intent?.let(::handleIntent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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
