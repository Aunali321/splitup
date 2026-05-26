package app.splitup.shared.data.repository

import app.splitup.shared.data.local.dao.CommentDao
import app.splitup.shared.data.local.dao.SettlementDao
import app.splitup.shared.data.local.dao.UserPreferencesDao
import app.splitup.shared.data.mapper.toDomain
import app.splitup.shared.data.mapper.toEntity
import app.splitup.shared.domain.model.Comment
import app.splitup.shared.domain.model.CommentId
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.Settlement
import app.splitup.shared.domain.model.SettlementId
import app.splitup.shared.domain.model.UserPreferences
import app.splitup.shared.domain.repository.CommentRepository
import app.splitup.shared.domain.repository.SettlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class RoomSettlementRepository(
    private val dao: SettlementDao,
    private val clock: Clock = Clock.System,
) : SettlementRepository {
    override fun observeInGroup(groupId: GroupId): Flow<List<Settlement>> =
        dao.observeInGroup(groupId.value).map { l -> l.map { it.toDomain() } }

    override fun observeBetween(a: PersonId, b: PersonId): Flow<List<Settlement>> =
        dao.observeBetween(a.value, b.value).map { l -> l.map { it.toDomain() } }

    override suspend fun save(settlement: Settlement) = dao.upsert(settlement.toEntity())

    override suspend fun delete(id: SettlementId) =
        dao.softDelete(id.value, clock.now().toEpochMilliseconds())
}

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
    suspend fun save(preferences: UserPreferences)
}

class RoomUserPreferencesRepository(
    private val dao: UserPreferencesDao,
) : UserPreferencesRepository {
    override fun observe(): Flow<UserPreferences> =
        dao.observe().map { it?.toDomain() ?: UserPreferences() }

    override suspend fun get(): UserPreferences = dao.get()?.toDomain() ?: UserPreferences()

    override suspend fun save(preferences: UserPreferences) =
        dao.upsert(preferences.toEntity())
}
