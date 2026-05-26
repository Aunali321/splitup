package app.splitup.shared.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Per-target factory that knows where to put the SQLite file. Each platform implements
 * this and the builder shape is then finalised in common code with [build].
 */
expect class DatabaseFactory {
    fun roomBuilder(): RoomDatabase.Builder<SplitUpDatabase>
}

fun DatabaseFactory.build(): SplitUpDatabase =
    roomBuilder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
        .build()
