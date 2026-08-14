package app.splitup.shared.data.repository

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import app.splitup.shared.data.local.SplitUpDatabase
import app.splitup.shared.data.local.dao.CommentDao
import app.splitup.shared.data.local.dao.MaintenanceDao
import app.splitup.shared.data.local.dao.UserPreferencesDao
import app.splitup.shared.data.sync.SyncService
import app.splitup.shared.data.sync.SyncTriggers
import app.splitup.shared.data.mapper.toDomain
import app.splitup.shared.data.mapper.toEntity
import app.splitup.shared.domain.model.Comment
import app.splitup.shared.domain.model.CommentId
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.UserPreferences
import app.splitup.shared.domain.repository.CommentRepository
import app.splitup.shared.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class RoomCommentRepository(
    private val dao: CommentDao,
    private val clock: Clock = Clock.System,
) : CommentRepository {
    override fun observeForExpense(id: ExpenseId): Flow<List<Comment>> =
        dao.observeForExpense(id.value).map { l -> l.map { it.toDomain() } }

    override suspend fun add(comment: Comment) = dao.upsert(comment.toEntity())

    override suspend fun delete(id: CommentId) =
        dao.softDelete(id.value, clock.now().toEpochMilliseconds())
}

interface UserPreferencesRepository {
    /** Always emits a value — defaults if the DB has no row yet (first launch). */
    fun observe(): Flow<UserPreferences>
    suspend fun get(): UserPreferences

    /** Atomic read-modify-write — concurrent updates can never revert each other. */
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}

class RoomUserPreferencesRepository(
    private val dao: UserPreferencesDao,
) : UserPreferencesRepository {
    private val writeLock = Mutex()

    override fun observe(): Flow<UserPreferences> =
        dao.observe().map { it?.toDomain() ?: UserPreferences() }

    override suspend fun get(): UserPreferences = dao.get()?.toDomain() ?: UserPreferences()

    override suspend fun update(transform: (UserPreferences) -> UserPreferences) =
        writeLock.withLock { dao.upsert(transform(get()).toEntity()) }
}

class RoomTransactionRunner(private val db: SplitUpDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        db.useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
}

/** Wipes every table in the local database. Backs Settings → "Erase all data". */
interface LocalDataReset {
    suspend fun clearAll()
}

class RoomLocalDataReset(
    private val dao: MaintenanceDao,
    private val syncTriggers: SyncTriggers,
    private val sync: SyncService,
) : LocalDataReset {
    /**
     * Triggers come off and the upload queue is purged first, so a local erase can
     * never replay as a server-side wipe of groups other people are in. PowerSync's
     * own replication state goes too, or a later sign-in resumes from a checkpoint
     * that considers every erased row already delivered.
     */
    override suspend fun clearAll() {
        syncTriggers.remove()
        syncTriggers.purgeQueue()
        sync.resetLocalSyncState()
        dao.clearAll()
    }
}
