package app.splitup.shared.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "settlement",
    indices = [
        Index("group_id"),
        Index("from_person_id"),
        Index("to_person_id"),
        Index("external_source", "external_id", unique = true),
    ],
)
data class SettlementEntity(
    @PrimaryKey val id: String,
    val group_id: String?,
    val from_person_id: String,
    val to_person_id: String,
    val amount_minor_units: Long,
    val currency_code: String,
    val date: LocalDate,
    val method: String,
    val notes: String?,
    val external_source: String?,
    val external_id: String?,
    val created_at: Instant,
    val updated_at: Instant,
    val deleted_at: Instant?,
)

@Entity(
    tableName = "comment",
    indices = [Index("expense_id"), Index("author_id")],
)
data class CommentEntity(
    @PrimaryKey val id: String,
    val expense_id: String,
    val author_id: String,
    val content: String,
    val created_at: Instant,
    val updated_at: Instant,
    val deleted_at: Instant?,
)

@Entity(
    tableName = "category",
    indices = [Index("parent_id")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val parent_id: String?,
    val name: String,
    val icon: String,
    val sort_order: Int,
)

@Entity(
    tableName = "exchange_rate",
    primaryKeys = ["from_code", "to_code", "date"],
)
data class ExchangeRateEntity(
    val from_code: String,
    val to_code: String,
    /** rate * 10^8 */
    val rate8: Long,
    val date: LocalDate,
    val source: String,
    val fetched_at: Instant,
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    /** Singleton row; always id == 0. */
    @PrimaryKey val id: Int = 0,
    val home_currency_code: String,
    val convert_to_home_in_ui: Boolean,
    val fx_source: String,
    val locale: String?,
    val first_day_of_week: Int,
    val theme: String,
    val use_dynamic_color: Boolean,
    val decimal_separator: String?,
    val biometric_lock: Boolean,
    val push_enabled: Boolean,
    val onboarding_completed_at: Instant?,
)
