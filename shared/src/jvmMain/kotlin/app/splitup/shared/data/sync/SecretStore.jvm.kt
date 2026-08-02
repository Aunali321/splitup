package app.splitup.shared.data.sync

import app.splitup.shared.util.appDataDir
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Desktop has no OS keystore we can rely on across Linux/Windows/macOS, so the token
 * lives in the app data dir with owner-only permissions where the filesystem supports it.
 */
actual class SecretStore {
    private val dir = appDataDir().resolve("secrets").also { it.mkdirs() }

    private fun file(key: String) = dir.resolve(key)

    actual fun read(key: String): String? = file(key).takeIf { it.isFile }?.readText()

    actual fun write(key: String, value: String) {
        val f = file(key)
        f.writeText(value)
        runCatching {
            Files.setPosixFilePermissions(
                f.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }

    actual fun clear(key: String) {
        file(key).delete()
    }
}
