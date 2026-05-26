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
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

class RoomExpenseRepository(
    private val dao: ExpenseDao,
    private val clock: Clock = Clock.System,
) : ExpenseRepository {

    override fun observeInGroup(groupId: GroupId): Flow<List<Expense>> =
        dao.observeInGroup(groupId.value).map { hydrate(it) }

    override fun observeWithFriend(friendId: PersonId): Flow<List<Expense>> =
        dao.observeWithFriend(friendId.value).map { hydrate(it) }

    override fun observeRecent(limit: Int): Flow<List<Expense>> =
        dao.observeRecent(limit).map { hydrate(it) }

    override suspend fun get(id: ExpenseId): Expense? {
        val e = dao.get(id.value) ?: return null
        return e.toDomain(dao.getShares(e.id))
    }

    override suspend fun save(expense: Expense) =
        dao.upsertWithShares(expense.toEntity(), expense.shareEntities())

    override suspend fun saveAll(expenses: List<Expense>) {
        for (e in expenses) save(e)
    }

    override suspend fun softDelete(id: ExpenseId) =
        dao.softDelete(id.value, clock.now().toEpochMilliseconds())

    override suspend fun restore(id: ExpenseId) =
        dao.restore(id.value, clock.now().toEpochMilliseconds())

    override suspend fun findByExternalId(source: String, externalId: String): Expense? {
        val e = dao.findByExternal(source, externalId) ?: return null
        return e.toDomain(dao.getShares(e.id))
    }

    override suspend fun search(query: String, from: LocalDate?, to: LocalDate?): List<Expense> {
        val rows = dao.search(query, from, to)
        if (rows.isEmpty()) return emptyList()
        val shares = dao.getSharesForExpenses(rows.map { it.id })
        return rows.map { it.toDomain(shares) }
    }

    private suspend fun hydrate(rows: List<app.splitup.shared.data.local.entity.ExpenseEntity>): List<Expense> {
        if (rows.isEmpty()) return emptyList()
        val shares = dao.getSharesForExpenses(rows.map { it.id })
        return rows.map { it.toDomain(shares) }
    }
}
