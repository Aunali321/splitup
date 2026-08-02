package app.splitup.shared.data.sync

import app.splitup.shared.data.local.SplitUpDatabase
import app.splitup.shared.data.local.entity.PersonEntity
import app.splitup.shared.data.local.entity.SyncSeedEntity
import app.splitup.shared.data.mapper.toEntity
import app.splitup.shared.domain.repository.PersonRepository
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.integrations.room.RoomConnectionPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

sealed interface InviteResult {
    /** [claimToken] is set when the invitee has no account yet — share it with them. */
    data class Invited(val claimToken: String?) : InviteResult
    data object NotSignedIn : InviteResult
    data object NotAMember : InviteResult
    data class Failed(val message: String) : InviteResult
}

sealed interface SyncState {
    data object SignedOut : SyncState
    data object Connecting : SyncState
    data class Connected(val accountId: String) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Owns the sync lifecycle. Sign-in is optional: with no token the app is a purely
 * local Room app, PowerSync is never connected and no CRUD triggers exist.
 */
class SyncService(
    private val room: SplitUpDatabase,
    private val api: SyncApi,
    private val secrets: SecretStore,
    private val people: PersonRepository,
    private val config: SyncConfig,
    private val triggers: SyncTriggers,
    private val scope: CoroutineScope,
) : SessionHolder {

    private val _state = MutableStateFlow<SyncState>(SyncState.SignedOut)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _rejections = MutableStateFlow<List<Rejection>>(emptyList())
    val rejections: StateFlow<List<Rejection>> = _rejections.asStateFlow()

    private var db: PowerSyncDatabase? = null

    override fun token(): String? = secrets.read(SESSION_TOKEN_KEY)
    override fun accountId(): String? = secrets.read(ACCOUNT_ID_KEY)

    /** Reconnects a previously signed-in install. No-op when signed out. */
    suspend fun start() {
        if (token() == null) return
        connect()
    }

    suspend fun register(email: String, password: String, displayName: String, inviteCode: String?) =
        authenticate(inviteCode) { api.register(email, password, displayName) }

    suspend fun signIn(email: String, password: String, inviteCode: String?) =
        authenticate(inviteCode) { api.login(email, password) }

    private suspend fun authenticate(inviteCode: String?, call: suspend () -> AuthResponse) {
        _state.value = SyncState.Connecting
        runCatching {
            val auth = call()
            secrets.write(SESSION_TOKEN_KEY, auth.token)
            secrets.write(ACCOUNT_ID_KEY, auth.accountId)
            // Redeeming before claiming identity lets the invited person become
            // this account's canonical identity instead of needing a merge.
            inviteCode?.takeIf { it.isNotBlank() }?.let { redeem(auth.token, it) }
            claimIdentity(auth.token)
            connect()
        }.onFailure {
            secrets.clear(SESSION_TOKEN_KEY)
            secrets.clear(ACCOUNT_ID_KEY)
            _state.value = SyncState.Failed(it.message ?: "sign-in failed")
        }
    }

    /**
     * Links this account to the local "me" person. The server returns the canonical
     * id, which differs when the account already had a person — signing in on a
     * second device, or redeeming an invite — and the local rows are re-pointed at it.
     */
    private suspend fun claimIdentity(token: String) {
        val me = people.getMe() ?: return
        val canonical = api.claimIdentity(token, me.toEntity().toJson()) ?: return
        if (canonical != me.id.value) {
            room.maintenanceDao().repointPerson(from = me.id.value, to = canonical)
        }
    }

    private suspend fun redeem(token: String, code: String) {
        api.claimInvite(token, code) ?: error("Invalid invite code")
    }

    /** Redeems an invite code while already signed in; the group arrives over sync. */
    suspend fun redeemInvite(code: String): String? {
        val token = token() ?: return "Sign in first"
        return runCatching { redeem(token, code.trim()) }
            .fold(onSuccess = { null }, onFailure = { it.message ?: "invalid invite code" })
    }

    private suspend fun connect() {
        val accountId = accountId() ?: return
        val pool = RoomConnectionPool(room, syncSchema)
        val powerSync = PowerSyncDatabase.opened(
            pool = pool,
            scope = scope,
            schema = syncSchema,
            identifier = "splitup",
            logger = Logger,
        )
        triggers.install()
        seedOnce(accountId)
        powerSync.connect(
            SplitUpConnector(api, this, config.requirePowerSyncUrl()) { _rejections.value = it },
        )
        db = powerSync
        _state.value = SyncState.Connected(accountId)
    }

    /**
     * Triggers only capture writes made after they exist, so the first connect for
     * an account uploads everything created before sign-in in one seeded batch.
     */
    private suspend fun seedOnce(accountId: String) {
        val seeds = room.syncSeedDao()
        if (seeds.isSeeded(accountId)) return
        triggers.seed()
        seeds.markSeeded(SyncSeedEntity(accountId))
    }

    /**
     * Adds someone to a group by email. The server resolves them to a canonical person
     * and the resulting rows arrive over sync, so nothing is written locally here.
     */
    suspend fun invite(groupId: String, email: String): InviteResult {
        val token = token() ?: return InviteResult.NotSignedIn
        return runCatching { api.invite(token, groupId, email) }
            .fold(
                onSuccess = { if (it == null) InviteResult.NotAMember else InviteResult.Invited(it.claimToken) },
                onFailure = { InviteResult.Failed(it.message ?: "invite failed") },
            )
    }

    /** Disconnects and forgets the session. Local data stays on the device. */
    suspend fun signOut() {
        token()?.let { runCatching { api.logout(it) } }
        db?.disconnect()
        db = null
        triggers.remove()
        secrets.clear(SESSION_TOKEN_KEY)
        secrets.clear(ACCOUNT_ID_KEY)
        _state.value = SyncState.SignedOut
    }
}

/** The identity endpoint takes the same row shape the sync triggers emit. */
private fun PersonEntity.toJson(): JsonObject =
    buildJsonObject {
        put("id", JsonPrimitive(id))
        put("account_id", JsonPrimitive(account_id))
        put("first_name", JsonPrimitive(first_name))
        put("last_name", JsonPrimitive(last_name))
        put("email", JsonPrimitive(email))
        put("phone", JsonPrimitive(phone))
        put("avatar_url", JsonPrimitive(avatar_url))
        put("default_currency_code", JsonPrimitive(default_currency_code))
        put("country_code", JsonPrimitive(country_code))
        put("is_registered", JsonPrimitive(if (is_registered) 1 else 0))
        put("external_source", JsonPrimitive(external_source))
        put("external_id", JsonPrimitive(external_id))
        put("updated_at", JsonPrimitive(updated_at.toString()))
        put("deleted_at", JsonPrimitive(deleted_at?.toString()))
    }
