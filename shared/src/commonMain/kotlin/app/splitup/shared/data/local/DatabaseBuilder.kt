package app.splitup.shared.data.local

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.powersync.integrations.room.loadPowerSyncExtension
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
        // The extension has to be loaded on the driver Room opens, because PowerSync
        // reads and writes through this same connection rather than its own database.
        .setDriver(BundledSQLiteDriver().also { it.loadPowerSyncExtension() })
        .setQueryCoroutineContext(Dispatchers.IO)
        // Pre-release: schema changes wipe and recreate rather than migrate.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
