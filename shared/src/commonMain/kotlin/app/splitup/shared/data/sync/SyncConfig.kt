package app.splitup.shared.data.sync

private const val SERVER_URL_KEY = "server_url"
private const val POWERSYNC_URL_KEY = "powersync_url"

/**
 * Where this install syncs to. Self-hosting is the norm for this app, so the user
 * supplies both addresses when they sign in; there is no default worth guessing.
 */
class SyncConfig(private val secrets: SecretStore) {
    var serverUrl: String?
        get() = secrets.read(SERVER_URL_KEY)
        set(value) = set(SERVER_URL_KEY, value)

    var powerSyncUrl: String?
        get() = secrets.read(POWERSYNC_URL_KEY)
        set(value) = set(POWERSYNC_URL_KEY, value)

    val isConfigured: Boolean get() = serverUrl != null && powerSyncUrl != null

    private fun set(key: String, value: String?) =
        if (value == null) secrets.clear(key) else secrets.write(key, value)

    fun requireServerUrl(): String =
        checkNotNull(serverUrl) { "no server configured" }

    fun requirePowerSyncUrl(): String =
        checkNotNull(powerSyncUrl) { "no PowerSync endpoint configured" }
}
