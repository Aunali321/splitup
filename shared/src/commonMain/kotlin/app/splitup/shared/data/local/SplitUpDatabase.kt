package app.splitup.shared.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverters
import app.splitup.shared.data.local.dao.CommentDao
import app.splitup.shared.data.local.dao.ExpenseDao
import app.splitup.shared.data.local.dao.GroupDao
import app.splitup.shared.data.local.dao.MaintenanceDao
import app.splitup.shared.data.local.dao.PersonDao
import app.splitup.shared.data.local.dao.SyncSeedDao
import app.splitup.shared.data.local.dao.UserPreferencesDao
import app.splitup.shared.data.local.entity.CommentEntity
import app.splitup.shared.data.local.entity.ExpenseEntity
import app.splitup.shared.data.local.entity.ExpenseShareEntity
import app.splitup.shared.data.local.entity.GroupEntity
import app.splitup.shared.data.local.entity.GroupMemberEntity
import app.splitup.shared.data.local.entity.PersonEntity
import app.splitup.shared.data.local.entity.SyncSeedEntity
import app.splitup.shared.data.local.entity.UserPreferencesEntity

@Database(
    entities = [
        PersonEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ExpenseEntity::class,
        ExpenseShareEntity::class,
        CommentEntity::class,
        SyncSeedEntity::class,
        UserPreferencesEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@ColumnTypeConverters(Converters::class)
@ConstructedBy(SplitUpDatabaseConstructor::class)
abstract class SplitUpDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun commentDao(): CommentDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun syncSeedDao(): SyncSeedDao
}

/**
 * Required by Room KMP: the compiler generates this object on each target, providing
 * the constructor of the generated database implementation. Don't implement manually.
 */
@Suppress("KotlinNoActualForExpect")
expect object SplitUpDatabaseConstructor : RoomDatabaseConstructor<SplitUpDatabase> {
    override fun initialize(): SplitUpDatabase
}
