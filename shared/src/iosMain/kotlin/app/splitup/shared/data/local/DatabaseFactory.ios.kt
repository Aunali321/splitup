package app.splitup.shared.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual class DatabaseFactory {
    actual fun roomBuilder(): RoomDatabase.Builder<SplitUpDatabase> {
        val docs = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        val path = requireNotNull(docs?.path) { "iOS Documents directory unavailable" } + "/splitup.db"
        return Room.databaseBuilder<SplitUpDatabase>(name = path)
    }
}
