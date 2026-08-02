package app.splitup.shared.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import app.splitup.shared.data.local.entity.CommentEntity
import app.splitup.shared.data.local.entity.SyncSeedEntity
import app.splitup.shared.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentDao {
    @Query("SELECT * FROM comment WHERE expense_id = :id AND deleted_at IS NULL ORDER BY created_at ASC")
    fun observeForExpense(id: String): Flow<List<CommentEntity>>

    @Upsert suspend fun upsert(comment: CommentEntity)
    @Query("UPDATE comment SET deleted_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface MaintenanceDao {
    /**
     * external ids stay behind as NULL: the copy would trip the unique
     * (external_source, external_id) index while the source row still exists,
     * and the synced canonical row is authoritative for them anyway.
     */
    @Query(
        """
        INSERT OR IGNORE INTO person (id, account_id, first_name, last_name, email, phone,
            avatar_url, default_currency_code, country_code, is_me, is_registered,
            external_source, external_id, updated_at, deleted_at)
        SELECT :to, account_id, first_name, last_name, email, phone,
            avatar_url, default_currency_code, country_code, 1, is_registered,
            NULL, NULL, updated_at, deleted_at
        FROM person WHERE id = :from
        """,
    )
    suspend fun copyPersonAs(from: String, to: String)

    @Query(
        """
        DELETE FROM expense_share WHERE person_id = :from
          AND expense_id IN (SELECT expense_id FROM expense_share WHERE person_id = :to)
        """,
    )
    suspend fun dropCollidingShares(from: String, to: String)

    @Query("UPDATE expense_share SET person_id = :to, id = expense_id || ':' || :to WHERE person_id = :from")
    suspend fun repointShares(from: String, to: String)

    @Query(
        """
        DELETE FROM group_member WHERE person_id = :from
          AND group_id IN (SELECT group_id FROM group_member WHERE person_id = :to)
        """,
    )
    suspend fun dropCollidingMemberships(from: String, to: String)

    @Query("UPDATE group_member SET person_id = :to, id = group_id || ':' || :to WHERE person_id = :from")
    suspend fun repointMemberships(from: String, to: String)

    @Query("UPDATE expense SET created_by = :to WHERE created_by = :from")
    suspend fun repointAuthoredExpenses(from: String, to: String)

    @Query("UPDATE comment SET author_id = :to WHERE author_id = :from")
    suspend fun repointComments(from: String, to: String)

    @Query("UPDATE person SET is_me = 1 WHERE id = :id")
    suspend fun markMe(id: String)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun deletePerson(id: String)

    /**
     * Signing in on a second device finds the account already has a person, so the
     * locally-created one is folded into the canonical row rather than duplicated.
     * The canonical row usually hasn't synced down yet, so it is created here as a
     * copy of the local one — the download upsert later overwrites every synced
     * column while leaving is_me alone.
     */
    @Transaction
    suspend fun repointPerson(from: String, to: String) {
        copyPersonAs(from, to)
        dropCollidingShares(from, to)
        repointShares(from, to)
        dropCollidingMemberships(from, to)
        repointMemberships(from, to)
        repointAuthoredExpenses(from, to)
        repointComments(from, to)
        deletePerson(from)
        markMe(to)
    }

    @Query("DELETE FROM expense_share") suspend fun clearExpenseShares()
    @Query("DELETE FROM expense") suspend fun clearExpenses()
    @Query("DELETE FROM comment") suspend fun clearComments()
    @Query("DELETE FROM group_member") suspend fun clearGroupMembers()
    @Query("DELETE FROM group_") suspend fun clearGroups()
    @Query("DELETE FROM person") suspend fun clearPeople()
    @Query("DELETE FROM user_preferences") suspend fun clearPreferences()
    @Query("DELETE FROM sync_seed") suspend fun clearSyncSeeds()

    /** Wipes every row. Children are cleared before parents so foreign keys never block. */
    @Transaction
    suspend fun clearAll() {
        clearExpenseShares()
        clearExpenses()
        clearComments()
        clearGroupMembers()
        clearGroups()
        clearPeople()
        clearPreferences()
        clearSyncSeeds()
    }
}

@Dao
interface SyncSeedDao {
    @Query("SELECT EXISTS(SELECT 1 FROM sync_seed WHERE account_id = :accountId)")
    suspend fun isSeeded(accountId: String): Boolean

    @Upsert suspend fun markSeeded(seed: SyncSeedEntity)
}

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 0 LIMIT 1")
    fun observe(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = 0 LIMIT 1")
    suspend fun get(): UserPreferencesEntity?

    @Upsert suspend fun upsert(prefs: UserPreferencesEntity)
}
