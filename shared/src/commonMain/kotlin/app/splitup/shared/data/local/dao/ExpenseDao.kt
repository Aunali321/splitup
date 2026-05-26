package app.splitup.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.splitup.shared.data.local.entity.ExpenseEntity
import app.splitup.shared.data.local.entity.ExpenseShareEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface ExpenseDao {

    @Query("""
        SELECT * FROM expense
        WHERE group_id = :groupId AND deleted_at IS NULL
        ORDER BY date DESC, created_at DESC
    """)
    fun observeInGroup(groupId: String): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT e.* FROM expense e
        INNER JOIN expense_share s ON s.expense_id = e.id
        WHERE e.group_id IS NULL AND s.person_id = :friendId AND e.deleted_at IS NULL
        ORDER BY e.date DESC, e.created_at DESC
    """)
    fun observeWithFriend(friendId: String): Flow<List<ExpenseEntity>>

    @Query("""
        SELECT * FROM expense
        WHERE deleted_at IS NULL
        ORDER BY date DESC, created_at DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ExpenseEntity?

    @Query("SELECT * FROM expense_share WHERE expense_id = :expenseId")
    suspend fun getShares(expenseId: String): List<ExpenseShareEntity>

    @Query("SELECT * FROM expense_share WHERE expense_id IN (:ids)")
    suspend fun getSharesForExpenses(ids: List<String>): List<ExpenseShareEntity>

    @Query("""
        SELECT * FROM expense
        WHERE external_source = :source AND external_id = :externalId
        LIMIT 1
    """)
    suspend fun findByExternal(source: String, externalId: String): ExpenseEntity?

    @Query("""
        SELECT * FROM expense
        WHERE deleted_at IS NULL
          AND (description LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
          AND (:from IS NULL OR date >= :from)
          AND (:to IS NULL OR date <= :to)
        ORDER BY date DESC
        LIMIT 200
    """)
    suspend fun search(query: String, from: LocalDate?, to: LocalDate?): List<ExpenseEntity>

    @Upsert suspend fun upsert(expense: ExpenseEntity)
    @Upsert suspend fun upsertAll(expenses: List<ExpenseEntity>)
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

    @Query("UPDATE expense SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)
}
