package app.splitup.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.splitup.shared.data.local.entity.CategoryEntity
import app.splitup.shared.data.local.entity.CommentEntity
import app.splitup.shared.data.local.entity.ExchangeRateEntity
import app.splitup.shared.data.local.entity.SettlementEntity
import app.splitup.shared.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlement WHERE group_id = :groupId AND deleted_at IS NULL ORDER BY date DESC")
    fun observeInGroup(groupId: String): Flow<List<SettlementEntity>>

    @Query("""
        SELECT * FROM settlement
        WHERE deleted_at IS NULL
          AND ((from_person_id = :a AND to_person_id = :b) OR (from_person_id = :b AND to_person_id = :a))
        ORDER BY date DESC
    """)
    fun observeBetween(a: String, b: String): Flow<List<SettlementEntity>>

    @Upsert suspend fun upsert(settlement: SettlementEntity)
    @Query("UPDATE settlement SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comment WHERE expense_id = :id AND deleted_at IS NULL ORDER BY created_at ASC")
    fun observeForExpense(id: String): Flow<List<CommentEntity>>

    @Upsert suspend fun upsert(comment: CommentEntity)
    @Query("UPDATE comment SET deleted_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY sort_order ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Upsert suspend fun upsertAll(categories: List<CategoryEntity>)
}

@Dao
interface ExchangeRateDao {
    @Query("""
        SELECT * FROM exchange_rate
        WHERE from_code = :from AND to_code = :to AND date <= :date
        ORDER BY date DESC
        LIMIT 1
    """)
    suspend fun nearest(from: String, to: String, date: LocalDate): ExchangeRateEntity?

    @Upsert suspend fun upsertAll(rates: List<ExchangeRateEntity>)
}

@Dao
interface MaintenanceDao {
    @Query("DELETE FROM expense_share") suspend fun clearExpenseShares()
    @Query("DELETE FROM expense") suspend fun clearExpenses()
    @Query("DELETE FROM settlement") suspend fun clearSettlements()
    @Query("DELETE FROM comment") suspend fun clearComments()
    @Query("DELETE FROM group_member") suspend fun clearGroupMembers()
    @Query("DELETE FROM group_") suspend fun clearGroups()
    @Query("DELETE FROM person") suspend fun clearPeople()
    @Query("DELETE FROM category") suspend fun clearCategories()
    @Query("DELETE FROM exchange_rate") suspend fun clearExchangeRates()
    @Query("DELETE FROM user_preferences") suspend fun clearPreferences()

    /** Wipes every row. Children are cleared before parents so foreign keys never block. */
    @Transaction
    suspend fun clearAll() {
        clearExpenseShares()
        clearExpenses()
        clearSettlements()
        clearComments()
        clearGroupMembers()
        clearGroups()
        clearPeople()
        clearCategories()
        clearExchangeRates()
        clearPreferences()
    }
}

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 0 LIMIT 1")
    fun observe(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = 0 LIMIT 1")
    suspend fun get(): UserPreferencesEntity?

    @Upsert suspend fun upsert(prefs: UserPreferencesEntity)
}
