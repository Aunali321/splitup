package app.splitup.shared.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
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

    @Query("DELETE FROM expense_share WHERE expense_id IN (SELECT id FROM expense WHERE group_id = :groupId)")
    suspend fun deleteExpenseShares(groupId: String)

    @Query("DELETE FROM comment WHERE expense_id IN (SELECT id FROM expense WHERE group_id = :groupId)")
    suspend fun deleteExpenseComments(groupId: String)

    @Query("DELETE FROM expense WHERE group_id = :groupId")
    suspend fun deleteExpenses(groupId: String)

    @Query("DELETE FROM group_ WHERE id = :id")
    suspend fun deleteRow(id: String)

    /**
     * Removes the group and everything in it. The group row goes before the
     * memberships: the sync triggers replay this order, and the server authorizes
     * a group DELETE against the caller's still-existing membership, cascading the
     * membership rows itself.
     */
    @Transaction
    suspend fun delete(id: String) {
        deleteExpenseShares(id)
        deleteExpenseComments(id)
        deleteExpenses(id)
        deleteRow(id)
        replaceMembers(id)
    }
}
