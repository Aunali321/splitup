package app.splitup.shared.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import app.splitup.shared.data.local.entity.ExpenseEntity
import app.splitup.shared.data.local.entity.ExpenseShareEntity
import app.splitup.shared.data.local.entity.ExpenseWithShares
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface ExpenseDao {

    @Transaction
    @Query("""
        SELECT * FROM expense
        WHERE group_id = :groupId AND deleted_at IS NULL
        ORDER BY date DESC, created_at DESC
    """)
    fun observeInGroup(groupId: String): Flow<List<ExpenseWithShares>>

    @Transaction
    @Query("""
        SELECT * FROM expense
        WHERE group_id IS NULL AND deleted_at IS NULL
        ORDER BY date DESC, created_at DESC
    """)
    fun observeNonGroup(): Flow<List<ExpenseWithShares>>

    @Transaction
    @Query("""
        SELECT e.* FROM expense e
        INNER JOIN expense_share s ON s.expense_id = e.id
        WHERE e.group_id IS NULL AND s.person_id = :friendId AND e.deleted_at IS NULL
        ORDER BY e.date DESC, e.created_at DESC
    """)
    fun observeWithFriend(friendId: String): Flow<List<ExpenseWithShares>>

    @Transaction
    @Query("""
        SELECT * FROM expense
        WHERE deleted_at IS NULL
        ORDER BY date DESC, created_at DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int): Flow<List<ExpenseWithShares>>

    /** Activity feed: includes soft-deleted rows; updated_at moves on every event. */
    @Transaction
    @Query("""
        SELECT * FROM expense
        ORDER BY updated_at DESC
        LIMIT :limit
    """)
    fun observeFeed(limit: Int): Flow<List<ExpenseWithShares>>

    @Transaction
    @Query("""
        SELECT * FROM expense
        WHERE deleted_at IS NULL
        ORDER BY date DESC, created_at DESC
    """)
    fun observeAll(): Flow<List<ExpenseWithShares>>

    @Transaction
    @Query("SELECT * FROM expense WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ExpenseWithShares?

    @Transaction
    @Query("SELECT * FROM expense WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ExpenseWithShares?>

    @Transaction
    @Query("""
        SELECT * FROM expense
        WHERE external_source = :source AND external_id = :externalId
        LIMIT 1
    """)
    suspend fun findByExternal(source: String, externalId: String): ExpenseWithShares?

    /** [pattern] is a pre-escaped LIKE pattern; literal %/_ are escaped with backslash. */
    @Transaction
    @Query("""
        SELECT * FROM expense
        WHERE deleted_at IS NULL
          AND (description LIKE :pattern ESCAPE '\' OR notes LIKE :pattern ESCAPE '\')
          AND (:from IS NULL OR date >= :from)
          AND (:to IS NULL OR date <= :to)
        ORDER BY date DESC
        LIMIT 200
    """)
    suspend fun search(pattern: String, from: LocalDate?, to: LocalDate?): List<ExpenseWithShares>

    @Upsert suspend fun upsert(expense: ExpenseEntity)
    @Upsert suspend fun upsertShares(shares: List<ExpenseShareEntity>)

    @Query("DELETE FROM expense_share WHERE expense_id = :expenseId")
    suspend fun clearShares(expenseId: String)

    @Transaction
    suspend fun upsertWithShares(expense: ExpenseEntity, shares: List<ExpenseShareEntity>) {
        upsert(expense)
        clearShares(expense.id)
        upsertShares(shares)
    }

    @Query("UPDATE expense SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
