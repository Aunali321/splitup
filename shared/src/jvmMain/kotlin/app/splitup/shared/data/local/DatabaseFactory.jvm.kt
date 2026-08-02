package app.splitup.shared.data.local

import androidx.room3.Room
import androidx.room3.RoomDatabase
import app.splitup.shared.util.appDataDir
import java.io.File

actual class DatabaseFactory(private val dataDir: File = appDataDir()) {
    actual fun roomBuilder(): RoomDatabase.Builder<SplitUpDatabase> {
        dataDir.mkdirs()
        val dbFile = File(dataDir, "splitup.db")
        return Room.databaseBuilder<SplitUpDatabase>(name = dbFile.absolutePath)
    }
}
