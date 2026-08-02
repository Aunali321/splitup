package app.splitup.ui.screens.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.splitup.shared.splitwise.SharedSplitwiseCredentials
import app.splitup.shared.splitwise.SplitwiseImporter
import app.splitup.shared.splitwise.SplitwiseOAuth
import app.splitup.ui.oauth.BrowserLauncher
import app.splitup.ui.oauth.OAuthCallbackBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SplitwiseImportViewModel(
    private val importer: SplitwiseImporter,
    private val oauth: SplitwiseOAuth,
    private val callbacks: OAuthCallbackBus,
    private val browser: BrowserLauncher,
) : ViewModel() {

    sealed interface Status {
        data object Idle : Status
        data object AwaitingBrowser : Status
        data class Running(val phase: String, val progress: Float) : Status
        data class Done(val people: Int, val groups: Int, val expenses: Int) : Status
        data class Failed(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** False when this platform cannot receive the OAuth redirect (desktop). */
    val oauthSupported: Boolean = browser.handlesOAuthRedirect

    private var pendingState: String? = null
    private var pendingPkce: SplitwiseOAuth.Pkce? = null

    init {
        viewModelScope.launch {
            callbacks.events
                .filter { it.provider == "splitwise" }
                .collect { handleCallback(it) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun startOAuthFlow() {
        viewModelScope.launch {
            val state = Uuid.random().toString()
            val pkce = oauth.createPkce()
            pendingState = state
            pendingPkce = pkce
            _status.value = Status.AwaitingBrowser
            browser.open(oauth.buildAuthorizeUrl(state, pkce.challenge))
        }
    }

    fun cancelOAuth() {
        pendingState = null
        pendingPkce = null
        _status.value = Status.Idle
    }

    /** Power-user path: paste an existing Splitwise access token / API key. */
    fun importWithToken(token: String) = runImport(token)

    private suspend fun handleCallback(payload: OAuthCallbackBus.Payload) {
        // One shot per authorization attempt — a replayed redirect finds nothing.
        val expectedState = pendingState
        val pkce = pendingPkce
        pendingState = null
        pendingPkce = null
        if (payload.error != null) {
            _status.value = Status.Failed(payload.error)
            return
        }
        if (expectedState == null || pkce == null || payload.state != expectedState) {
            _status.value = Status.Failed("OAuth state mismatch — refused as a CSRF guard")
            return
        }
        val code = payload.code ?: run {
            _status.value = Status.Failed("Splitwise did not return an authorization code")
            return
        }
        try {
            val tokens = oauth.exchangeCode(code, pkce.verifier)
            runImport(tokens.accessToken)
        } catch (t: Throwable) {
            _status.value = Status.Failed("Token exchange failed: ${t.message ?: t::class.simpleName}")
        }
    }

    private fun runImport(token: String) {
        viewModelScope.launch {
            try {
                val result = importer.run(token) { phase, progress ->
                    _status.value = Status.Running(phase, progress)
                }
                _status.value = Status.Done(result.peopleCount, result.groupCount, result.expenseCount)
            } catch (t: Throwable) {
                _status.value = Status.Failed(t.message ?: t::class.simpleName.orEmpty())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitwiseImportScreen(onDone: () -> Unit) {
    val vm: SplitwiseImportViewModel = koinViewModel()
    val status by vm.status.collectAsStateWithLifecycle()
    var manualToken by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(!vm.oauthSupported) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from Splitwise", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, contentDescription = "Close") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Sign in to Splitwise and approve access. We use a read-only OAuth scope, pull everything once, and never touch your Splitwise account again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val s = status) {
                SplitwiseImportViewModel.Status.Idle -> {
                    if (vm.oauthSupported) {
                        Button(onClick = vm::startOAuthFlow, modifier = Modifier.fillMaxWidth()) {
                            Text("Connect with Splitwise")
                        }
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))
                        TextButton(onClick = { showManual = !showManual }) {
                            Text(if (showManual) "Hide manual token entry" else "Have an access token?")
                        }
                    } else {
                        Text(
                            "This platform can't receive the Splitwise sign-in redirect. " +
                                "Create a personal API key at secure.splitwise.com/apps and paste it here.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (showManual) {
                        OutlinedTextField(
                            value = manualToken,
                            onValueChange = { manualToken = it },
                            label = { Text("Access token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Button(
                            onClick = { vm.importWithToken(manualToken.trim()) },
                            enabled = manualToken.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Import with token") }
                    }
                }
                SplitwiseImportViewModel.Status.AwaitingBrowser -> {
                    Text("Finish signing in in your browser…", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Redirect URI in use: ${SharedSplitwiseCredentials.PROD.redirectUri}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = vm::cancelOAuth) { Text("Cancel") }
                }
                is SplitwiseImportViewModel.Status.Running -> {
                    Text(s.phase, style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is SplitwiseImportViewModel.Status.Done -> {
                    Text(
                        "Imported ${s.people} people, ${s.groups} groups, ${s.expenses} expenses.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
                is SplitwiseImportViewModel.Status.Failed -> {
                    Text("Import failed: ${s.reason}", color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = if (vm.oauthSupported) vm::startOAuthFlow else vm::cancelOAuth,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Try again") }
                }
            }
        }
    }
}
