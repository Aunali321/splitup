package app.splitup.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.splitup.shared.data.sync.SyncConfig
import app.splitup.shared.data.sync.SyncService
import app.splitup.shared.data.sync.SyncState
import app.splitup.ui.components.ListDivider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class SyncForm(
    val serverUrl: String = "",
    val powerSyncUrl: String = "",
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val inviteCode: String = "",
    val registering: Boolean = false,
) {
    val canSubmit: Boolean
        get() = serverUrl.isNotBlank() && powerSyncUrl.isNotBlank() &&
            email.isNotBlank() && password.length >= 8 &&
            (!registering || displayName.isNotBlank())
}

class SyncViewModel(
    private val sync: SyncService,
    private val config: SyncConfig,
) : ViewModel() {
    val state: StateFlow<SyncState> = sync.state
    val rejections = sync.rejections

    private val _form = MutableStateFlow(
        SyncForm(
            serverUrl = config.serverUrl.orEmpty(),
            powerSyncUrl = config.powerSyncUrl.orEmpty(),
        ),
    )
    val form: StateFlow<SyncForm> = _form.asStateFlow()

    fun edit(update: (SyncForm) -> SyncForm) { _form.value = update(_form.value) }

    sealed interface RedeemState {
        data object Accepted : RedeemState
        data class Failed(val message: String) : RedeemState
    }

    private val _redeem = MutableStateFlow<RedeemState?>(null)
    val redeem: StateFlow<RedeemState?> = _redeem.asStateFlow()

    fun submit() {
        val f = _form.value
        config.serverUrl = f.serverUrl.trim()
        config.powerSyncUrl = f.powerSyncUrl.trim()
        viewModelScope.launch {
            if (f.registering) sync.register(f.email.trim(), f.password, f.displayName.trim(), f.inviteCode)
            else sync.signIn(f.email.trim(), f.password, f.inviteCode)
        }
    }

    fun redeemInvite(code: String) {
        viewModelScope.launch {
            _redeem.value = sync.redeemInvite(code)
                ?.let { RedeemState.Failed(it) } ?: RedeemState.Accepted
        }
    }

    fun signOut() = viewModelScope.launch { sync.signOut() }.let { }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val vm: SyncViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val rejections by vm.rejections.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is SyncState.Connected -> Connected(s, rejections.size, vm)
                SyncState.Connecting -> Row("Connecting…") { CircularProgressIndicator() }
                else -> SignInForm(form, (state as? SyncState.Failed)?.message, vm)
            }
        }
    }
}

@Composable
private fun Connected(state: SyncState.Connected, rejected: Int, vm: SyncViewModel) {
    Text("Signed in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Your groups sync with everyone in them. Account ${state.accountId}.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (rejected > 0) {
        Text(
            "$rejected change${if (rejected == 1) "" else "s"} were refused by the server. " +
                "This usually means you no longer have access to that group.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(Modifier.height(8.dp))
    ListDivider()
    RedeemInvite(vm)
    ListDivider()
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = vm::signOut, modifier = Modifier.fillMaxWidth()) {
        Text("Sign out")
    }
    Text(
        "Signing out keeps everything on this device; it only stops syncing.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RedeemInvite(vm: SyncViewModel) {
    val redeem by vm.redeem.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    Text(
        "Invited to a group? Enter the invite code you were sent.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Field("Invite code", code) { code = it }
    when (val r = redeem) {
        null -> {}
        SyncViewModel.RedeemState.Accepted -> Text(
            "Invite accepted — the group will appear once it syncs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        is SyncViewModel.RedeemState.Failed ->
            Text(r.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    OutlinedButton(
        onClick = { vm.redeemInvite(code) },
        enabled = code.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Redeem invite") }
}

@Composable
private fun SignInForm(form: SyncForm, error: String?, vm: SyncViewModel) {
    Text(
        if (form.registering) "Create an account" else "Sign in to sync",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        "SplitUp works fully offline without an account. Sign in only if you want your " +
            "groups shared across devices or with the people you split with.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Self-hosted: there is no default server worth guessing at.
    Field("Server address", form.serverUrl, KeyboardType.Uri) { v -> vm.edit { it.copy(serverUrl = v) } }
    Field("PowerSync address", form.powerSyncUrl, KeyboardType.Uri) { v -> vm.edit { it.copy(powerSyncUrl = v) } }
    Field("Email", form.email, KeyboardType.Email) { v -> vm.edit { it.copy(email = v) } }
    Field("Password", form.password, KeyboardType.Password, masked = true) { v ->
        vm.edit { it.copy(password = v) }
    }
    if (form.registering) {
        Field("Your name", form.displayName) { v -> vm.edit { it.copy(displayName = v) } }
    }
    Field("Invite code (optional)", form.inviteCode) { v -> vm.edit { it.copy(inviteCode = v) } }

    error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    Button(onClick = vm::submit, enabled = form.canSubmit, modifier = Modifier.fillMaxWidth()) {
        Text(if (form.registering) "Create account" else "Sign in")
    }
    TextButton(
        onClick = { vm.edit { it.copy(registering = !it.registering) } },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (form.registering) "I already have an account" else "Create an account instead")
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    masked: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (masked) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Row(text: String, trailing: @Composable () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
    trailing()
}
