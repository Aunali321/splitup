package app.splitup.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.splitup.shared.data.local.entity.GroupEntity
import app.splitup.shared.data.local.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM group_ WHERE (:includeArchived = 1 OR archived_at IS NULL) ORDER BY updated_at DESC")
    fun observeAll(includeArchived: Boolean): Flow<List<GroupEntity>>

    @Query("SELECT * FROM group_ WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM group_ WHERE id = :id LIMIT 1")
    suspend fun get(id: String): GroupEntity?

    @Query("SELECT * FROM group_ WHERE external_source = :source AND external_id = :externalId LIMIT 1")
    suspend fun findByExternal(source: String, externalId: String): GroupEntity?

    @Query("SELECT * FROM group_member WHERE group_id = :groupId")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_member WHERE group_id = :groupId")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>

    @Upsert suspend fun upsert(group: GroupEntity)
    @Upsert suspend fun upsertMembers(members: List<GroupMemberEntity>)

    @Transaction
    suspend fun upsertWithMembers(group: GroupEntity, members: List<GroupMemberEntity>) {
        upsert(group)
        replaceMembers(group.id)
        upsertMembers(members)
    }

    @Query("DELETE FROM group_member WHERE group_id = :groupId")
    suspend fun replaceMembers(groupId: String)

    @Query("UPDATE group_ SET archived_at = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long)

    @Query("DELETE FROM group_ WHERE id = :id")
    suspend fun delete(id: String)
}
