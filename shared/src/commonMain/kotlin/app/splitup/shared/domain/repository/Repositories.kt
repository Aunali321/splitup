package app.splitup.shared.domain.repository

import app.splitup.shared.domain.model.Comment
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface PersonRepository {
    fun observeAll(): Flow<List<Person>>
    fun observeFriends(): Flow<List<Person>>
    suspend fun get(id: PersonId): Person?
    fun observe(id: PersonId): Flow<Person?>
    fun observeMe(): Flow<Person>
    suspend fun getMe(): Person?
    suspend fun save(person: Person)
    suspend fun delete(id: PersonId)
    suspend fun findByExternal(source: String, externalId: String): Person?
}

interface GroupRepository {
    fun observeAll(includeArchived: Boolean = false): Flow<List<Group>>
    fun observe(id: GroupId): Flow<Group?>
    suspend fun get(id: GroupId): Group?
    suspend fun save(group: Group)
    suspend fun archive(id: GroupId)
    suspend fun delete(id: GroupId)
    suspend fun findByExternal(source: String, externalId: String): Group?
}

interface ExpenseRepository {
    fun observeInGroup(groupId: GroupId): Flow<List<Expense>>
    fun observeNonGroup(): Flow<List<Expense>>
    fun observeWithFriend(friendId: PersonId): Flow<List<Expense>>
    fun observeRecent(limit: Int = 50): Flow<List<Expense>>
    /** Newest-first by last event time, soft-deleted included — the activity feed. */
    fun observeFeed(limit: Int = 100): Flow<List<Expense>>
    fun observeAll(): Flow<List<Expense>>
    fun observe(id: ExpenseId): Flow<Expense?>
    suspend fun get(id: ExpenseId): Expense?
    suspend fun save(expense: Expense)
    suspend fun softDelete(id: ExpenseId)
    suspend fun findByExternalId(source: String, externalId: String): Expense?
    suspend fun search(query: String, from: LocalDate? = null, to: LocalDate? = null): List<Expense>
}

interface CommentRepository {
    fun observeForExpense(id: ExpenseId): Flow<List<Comment>>
    suspend fun add(comment: Comment)
    suspend fun delete(id: app.splitup.shared.domain.model.CommentId)
}

/** Runs [block] atomically: either every write inside commits, or none do. */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
