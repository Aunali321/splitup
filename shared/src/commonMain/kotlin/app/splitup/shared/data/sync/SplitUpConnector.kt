package app.splitup.shared.data.sync

import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.crud.UpdateType

/**
 * Bridges PowerSync to the splitup server.
 *
 * Uploads go to /sync/write, which authorizes every row against group membership.
 * Rejections come back inside a 200 and are logged rather than retried: a failed
 * response here would wedge the upload queue permanently, and a refused write is
 * not something a retry would fix.
 */
class SplitUpConnector(
    private val api: SyncApi,
    private val session: SessionHolder,
    private val powerSyncUrl: String,
    private val onRejected: (List<Rejection>) -> Unit = {},
) : PowerSyncBackendConnector() {

    override suspend fun fetchCredentials(): PowerSyncCredentials {
        val token = session.token() ?: error("not signed in")
        return PowerSyncCredentials(
            endpoint = powerSyncUrl,
            token = api.syncToken(token),
            userId = session.accountId(),
        )
    }

    override suspend fun uploadData(database: PowerSyncDatabase) {
        val batch = database.getCrudBatch() ?: return
        val token = session.token() ?: return

        val ops = batch.crud.mapNotNull { entry ->
            val table = SyncTable.of(entry.table) ?: return@mapNotNull null
            when (entry.op) {
                UpdateType.DELETE -> SyncOp(OpKind.DELETE, table, entry.id)
                // Triggers emit whole rows, so PATCH never reaches here; treat it as
                // a PUT anyway rather than silently dropping a write.
                UpdateType.PUT, UpdateType.PATCH ->
                    SyncOp(OpKind.PUT, table, entry.id, entry.opData?.jsonValues)
            }
        }
        if (ops.isEmpty()) {
            batch.complete(null)
            return
        }

        val response = api.write(token, ops)
        if (response.rejected.isNotEmpty()) onRejected(response.rejected)
        batch.complete(null)
    }
}

/** Read access to the current session, so the connector never owns the token. */
interface SessionHolder {
    fun token(): String?
    fun accountId(): String?
}
