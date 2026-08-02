package app.splitup.shared.data.local

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

actual class DatabaseFactory(private val context: Context) {
    actual fun roomBuilder(): RoomDatabase.Builder<SplitUpDatabase> {
        val dbFile = context.getDatabasePath("splitup.db")
        return Room.databaseBuilder<SplitUpDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath,
        )
    }
}
