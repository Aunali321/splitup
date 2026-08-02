package app.splitup.shared.data.sync

import platform.Foundation.NSUserDefaults

/**
 * Placeholder matching the rest of iosMain, which has never been compiled (no Mac).
 * Replace with a Keychain-backed implementation before shipping iOS.
 */
actual class SecretStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun read(key: String): String? = defaults.stringForKey(key)
    actual fun write(key: String, value: String) = defaults.setObject(value, key)
    actual fun clear(key: String) = defaults.removeObjectForKey(key)
}
