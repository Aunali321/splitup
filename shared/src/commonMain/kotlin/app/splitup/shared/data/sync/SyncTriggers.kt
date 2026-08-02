package app.splitup.shared.data.sync

import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import app.splitup.shared.data.local.SplitUpDatabase

/**
 * The SQLite side of upload capture. Triggers exist only while an account is
 * signed in — installing them on connect and removing them on sign-out keeps a
 * signed-out install from queueing (and later replaying) every local write.
 */
class SyncTriggers(private val db: SplitUpDatabase) {

    suspend fun install() = exec(crudTriggers)

    suspend fun remove() = exec(crudTriggerDrops)

    /** Enqueues all pre-sign-in rows for upload, atomically. Run once per account. */
    suspend fun seed(): Unit = db.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            crudSeeds.forEach { executeSQL(it) }
        }
    }

    /** Drops queued uploads; part of erasing all local data. */
    suspend fun purgeQueue(): Unit = db.useWriterConnection { transactor ->
        val exists = transactor.usePrepared(
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'ps_crud'",
        ) { it.step() && it.getLong(0) > 0 }
        if (exists) transactor.executeSQL("DELETE FROM ps_crud")
    }

    private suspend fun exec(statements: List<String>): Unit = db.useWriterConnection { transactor ->
        statements.forEach { transactor.executeSQL(it) }
    }
}
