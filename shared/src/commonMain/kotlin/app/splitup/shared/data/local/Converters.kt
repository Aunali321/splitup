package app.splitup.shared.data.local

import androidx.room3.ColumnTypeConverter
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Entities store ids and enums as plain strings; only the date types need converting. */
class Converters {
    @ColumnTypeConverter fun fromInstant(v: Instant?): Long? = v?.toEpochMilliseconds()
    @ColumnTypeConverter fun toInstant(v: Long?): Instant? = v?.let { Instant.fromEpochMilliseconds(it) }

    @ColumnTypeConverter fun fromLocalDate(v: LocalDate?): Long? = v?.toEpochDays()
    @ColumnTypeConverter fun toLocalDate(v: Long?): LocalDate? = v?.let { LocalDate.fromEpochDays(it) }
}
