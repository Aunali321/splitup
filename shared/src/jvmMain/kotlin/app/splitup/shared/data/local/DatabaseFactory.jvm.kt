package app.splitup.shared.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual class DatabaseFactory(private val dataDir: File = defaultDataDir()) {
    actual fun roomBuilder(): RoomDatabase.Builder<SplitUpDatabase> {
        dataDir.mkdirs()
        val dbFile = File(dataDir, "splitup.db")
        return Room.databaseBuilder<SplitUpDatabase>(name = dbFile.absolutePath)
    }

    companion object {
        private fun defaultDataDir(): File {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            return when {
                "mac" in os || "darwin" in os ->
                    File("$home/Library/Application Support/SplitUp")
                "win" in os ->
                    File(System.getenv("APPDATA") ?: home, "SplitUp")
                else -> {
                    val xdg = System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"
                    File("$xdg/splitup")
                }
            }
        }
    }
}
