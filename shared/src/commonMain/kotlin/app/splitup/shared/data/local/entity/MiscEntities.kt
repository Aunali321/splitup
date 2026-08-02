package app.splitup.shared.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant

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

/** Accounts whose pre-sign-in local rows have already been enqueued for upload. */
@Entity(tableName = "sync_seed")
data class SyncSeedEntity(
    @PrimaryKey val account_id: String,
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
