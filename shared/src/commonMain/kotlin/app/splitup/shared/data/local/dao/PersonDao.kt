package app.splitup.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.splitup.shared.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM person WHERE deleted_at IS NULL ORDER BY is_me DESC, first_name ASC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE deleted_at IS NULL AND is_me = 0 ORDER BY first_name ASC")
    fun observeFriends(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PersonEntity?

    @Query("SELECT * FROM person WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<PersonEntity?>

    @Query("SELECT * FROM person WHERE is_me = 1 LIMIT 1")
    fun observeMe(): Flow<PersonEntity?>

    @Query("SELECT * FROM person WHERE is_me = 1 LIMIT 1")
    suspend fun getMe(): PersonEntity?

    @Query("SELECT * FROM person WHERE external_source = :source AND external_id = :externalId LIMIT 1")
    suspend fun findByExternal(source: String, externalId: String): PersonEntity?

    @Upsert
    suspend fun upsert(person: PersonEntity)

    @Upsert
    suspend fun upsertAll(people: List<PersonEntity>)

    @Query("UPDATE person SET deleted_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
