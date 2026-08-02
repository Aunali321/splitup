package app.splitup.shared.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant

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
    /** JSON-encoded SplitStrategy, or null when new expenses default to equal. */
    val default_split_json: String?,
    val whiteboard: String?,
    val external_source: String?,
    val external_id: String?,
    val created_at: Instant,
    val updated_at: Instant,
    val archived_at: Instant?,
)

@Entity(
    tableName = "group_member",
    indices = [
        Index("group_id", "person_id", unique = true),
        Index("person_id"),
    ],
)
data class GroupMemberEntity(
    @PrimaryKey val id: String,
    val group_id: String,
    val person_id: String,
    val role: String,
    val joined_at: Instant,
)

fun memberId(groupId: String, personId: String): String = "$groupId:$personId"
