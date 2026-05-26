package app.splitup.shared.data.local

import androidx.room.TypeConverter
import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.ExternalSource
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.GroupRole
import app.splitup.shared.domain.model.GroupType
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.model.SettlementMethod
import app.splitup.shared.domain.model.ThemePreference
import app.splitup.shared.domain.model.FxSource
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class Converters {
    @TypeConverter fun fromInstant(v: Instant?): Long? = v?.toEpochMilliseconds()
    @TypeConverter fun toInstant(v: Long?): Instant? = v?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter fun fromLocalDate(v: LocalDate?): Int? = v?.toEpochDays()
    @TypeConverter fun toLocalDate(v: Int?): LocalDate? = v?.let { LocalDate.fromEpochDays(it) }

    @TypeConverter fun fromPersonId(v: PersonId?): String? = v?.value
    @TypeConverter fun toPersonId(v: String?): PersonId? = v?.let { PersonId(it) }
    @TypeConverter fun fromGroupId(v: GroupId?): String? = v?.value
    @TypeConverter fun toGroupId(v: String?): GroupId? = v?.let { GroupId(it) }
    @TypeConverter fun fromCategoryId(v: CategoryId?): String? = v?.value
    @TypeConverter fun toCategoryId(v: String?): CategoryId? = v?.let { CategoryId(it) }

    @TypeConverter fun fromRepeatInterval(v: RepeatInterval?): String? = v?.name
    @TypeConverter fun toRepeatInterval(v: String?): RepeatInterval? = v?.let { RepeatInterval.valueOf(it) }
    @TypeConverter fun fromGroupType(v: GroupType?): String? = v?.name
    @TypeConverter fun toGroupType(v: String?): GroupType? = v?.let { GroupType.valueOf(it) }
    @TypeConverter fun fromGroupRole(v: GroupRole?): String? = v?.name
    @TypeConverter fun toGroupRole(v: String?): GroupRole? = v?.let { GroupRole.valueOf(it) }
    @TypeConverter fun fromSettlementMethod(v: SettlementMethod?): String? = v?.name
    @TypeConverter fun toSettlementMethod(v: String?): SettlementMethod? = v?.let { SettlementMethod.valueOf(it) }
    @TypeConverter fun fromExternalSource(v: ExternalSource?): String? = v?.name
    @TypeConverter fun toExternalSource(v: String?): ExternalSource? = v?.let { ExternalSource.valueOf(it) }
    @TypeConverter fun fromTheme(v: ThemePreference?): String? = v?.name
    @TypeConverter fun toTheme(v: String?): ThemePreference? = v?.let { ThemePreference.valueOf(it) }
    @TypeConverter fun fromFxSource(v: FxSource?): String? = v?.name
    @TypeConverter fun toFxSource(v: String?): FxSource? = v?.let { FxSource.valueOf(it) }
}
