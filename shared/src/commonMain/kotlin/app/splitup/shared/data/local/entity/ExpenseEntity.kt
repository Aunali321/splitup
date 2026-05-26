package app.splitup.shared.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "expense",
    indices = [
        Index("group_id"),
        Index("created_by"),
        Index("date"),
        Index("external_source", "external_id", unique = true),
    ],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val group_id: String?,
    val description: String,
    /** Total cost in minor units of [currency_code]. */
    val cost_minor_units: Long,
    val currency_code: String,
    val date: LocalDate,
    val category_id: String,
    val notes: String?,
    val created_by: String,
    /** JSON-encoded SplitStrategy. The expanded shares live in [ExpenseShareEntity]. */
    val split_strategy_json: String,
    val is_payment: Boolean,
    val is_refund: Boolean,
    val receipt_url: String?,
    val repeat_interval: String,
    val next_repeat_at: Instant?,
    val bundle_id: String?,
    val external_source: String?,
    val external_id: String?,
    val created_at: Instant,
    val updated_at: Instant,
    val deleted_at: Instant?,
)

@Entity(
    tableName = "expense_share",
    primaryKeys = ["expense_id", "person_id"],
    indices = [Index("person_id")],
)
data class ExpenseShareEntity(
    val expense_id: String,
    val person_id: String,
    val paid_minor_units: Long,
    val owed_minor_units: Long,
)
