package app.splitup.shared.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "person",
    indices = [
        Index("email"),
        Index("phone"),
        Index("external_source", "external_id", unique = true),
    ],
)
data class PersonEntity(
    @PrimaryKey val id: String,
    val first_name: String,
    val last_name: String?,
    val email: String?,
    val phone: String?,
    val avatar_url: String?,
    val default_currency_code: String,
    val country_code: String?,
    val is_me: Boolean,
    val is_registered: Boolean,
    val external_source: String?,
    val external_id: String?,
    val updated_at: Instant,
    val deleted_at: Instant? = null,
)
