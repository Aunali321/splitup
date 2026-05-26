package app.splitup.shared.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import app.splitup.shared.data.local.dao.CategoryDao
import app.splitup.shared.data.local.dao.CommentDao
import app.splitup.shared.data.local.dao.ExchangeRateDao
import app.splitup.shared.data.local.dao.ExpenseDao
import app.splitup.shared.data.local.dao.GroupDao
import app.splitup.shared.data.local.dao.PersonDao
import app.splitup.shared.data.local.dao.SettlementDao
import app.splitup.shared.data.local.dao.UserPreferencesDao
import app.splitup.shared.data.local.entity.CategoryEntity
import app.splitup.shared.data.local.entity.CommentEntity
import app.splitup.shared.data.local.entity.ExchangeRateEntity
import app.splitup.shared.data.local.entity.ExpenseEntity
import app.splitup.shared.data.local.entity.ExpenseShareEntity
import app.splitup.shared.data.local.entity.GroupEntity
import app.splitup.shared.data.local.entity.GroupMemberEntity
import app.splitup.shared.data.local.entity.PersonEntity
import app.splitup.shared.data.local.entity.SettlementEntity
import app.splitup.shared.data.local.entity.UserPreferencesEntity

@Database(
    entities = [
        PersonEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ExpenseEntity::class,
        ExpenseShareEntity::class,
        SettlementEntity::class,
        CommentEntity::class,
        CategoryEntity::class,
        ExchangeRateEntity::class,
        UserPreferencesEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(SplitUpDatabaseConstructor::class)
abstract class SplitUpDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun settlementDao(): SettlementDao
    abstract fun commentDao(): CommentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun userPreferencesDao(): UserPreferencesDao
}

/**
 * Required by Room KMP: the compiler generates this object on each target, providing
 * the constructor of the generated database implementation. Don't implement manually.
 */
@Suppress("KotlinNoActualForExpect")
expect object SplitUpDatabaseConstructor : RoomDatabaseConstructor<SplitUpDatabase> {
    override fun initialize(): SplitUpDatabase
}
