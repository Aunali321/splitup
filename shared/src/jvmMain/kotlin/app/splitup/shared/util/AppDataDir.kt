package app.splitup.shared.util

import java.io.File

/** Per-OS application data directory shared by the database and file storage. */
fun appDataDir(): File {
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
