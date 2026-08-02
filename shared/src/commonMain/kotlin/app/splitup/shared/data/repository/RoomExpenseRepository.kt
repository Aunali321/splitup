package app.splitup.shared.data.repository

import app.splitup.shared.data.local.dao.ExpenseDao
import app.splitup.shared.data.mapper.shareEntities
import app.splitup.shared.data.mapper.toDomain
import app.splitup.shared.data.mapper.toEntity
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

class RoomExpenseRepository(
    private val dao: ExpenseDao,
    private val clock: Clock = Clock.System,
) : ExpenseRepository {

    override fun observeInGroup(groupId: GroupId): Flow<List<Expense>> =
        dao.observeInGroup(groupId.value).map { list -> list.map { it.toDomain() } }

    override fun observeNonGroup(): Flow<List<Expense>> =
        dao.observeNonGroup().map { list -> list.map { it.toDomain() } }

    override fun observeWithFriend(friendId: PersonId): Flow<List<Expense>> =
        dao.observeWithFriend(friendId.value).map { list -> list.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<Expense>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeFeed(limit: Int): Flow<List<Expense>> =
        dao.observeFeed(limit).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Expense>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observe(id: ExpenseId): Flow<Expense?> =
        dao.observeById(id.value).map { it?.toDomain() }

    override suspend fun get(id: ExpenseId): Expense? = dao.get(id.value)?.toDomain()

    override suspend fun save(expense: Expense) =
        dao.upsertWithShares(expense.toEntity(), expense.shareEntities())

    override suspend fun softDelete(id: ExpenseId) =
        dao.softDelete(id.value, clock.now().toEpochMilliseconds())

    override suspend fun findByExternalId(source: String, externalId: String): Expense? =
        dao.findByExternal(source, externalId)?.toDomain()

    override suspend fun search(query: String, from: LocalDate?, to: LocalDate?): List<Expense> =
        dao.search(likePattern(query), from, to).map { it.toDomain() }
}

private fun likePattern(query: String): String =
    "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
