package app.splitup.shared.data.sync

/** Persists the session token. Nothing else in the app stores a long-lived secret. */
expect class SecretStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun clear(key: String)
}

const val SESSION_TOKEN_KEY = "session_token"
const val ACCOUNT_ID_KEY = "account_id"
