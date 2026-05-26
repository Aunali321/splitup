package app.splitup.shared.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class DatabaseFactory(private val context: Context) {
    actual fun roomBuilder(): RoomDatabase.Builder<SplitUpDatabase> {
        val dbFile = context.getDatabasePath("splitup.db")
        return Room.databaseBuilder<SplitUpDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath,
        )
    }
}
