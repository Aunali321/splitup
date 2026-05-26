package app.splitup.shared.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "group_",
    indices = [Index("external_source", "external_id", unique = true)],
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val avatar_url: String?,
    val cover_url: String?,
    val default_currency_code: String,
    val simplify_by_default: Boolean,
    val whiteboard: String?,
    val external_source: String?,
    val external_id: String?,
    val created_at: Instant,
    val updated_at: Instant,
    val archived_at: Instant?,
)

@Entity(
    tableName = "group_member",
    primaryKeys = ["group_id", "person_id"],
    indices = [Index("person_id")],
)
data class GroupMemberEntity(
    val group_id: String,
    val person_id: String,
    val role: String,
    val joined_at: Instant,
)
