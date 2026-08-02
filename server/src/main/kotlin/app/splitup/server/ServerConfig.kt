package app.splitup.server

data class ServerConfig(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    /** 32 random bytes, base64url-encoded. Same value goes to PowerSync as the HS256 JWK. */
    val sessionSecret: String,
    val openExchangeRatesApiKey: String?,
    /**
     * Trust X-Forwarded-For for the client address. Only enable behind a reverse
     * proxy that overwrites the header — trusting it on a bare deployment lets
     * clients spoof their way past the auth rate limit.
     */
    val behindProxy: Boolean,
) {
    companion object {
        fun fromEnv(): ServerConfig = ServerConfig(
            port = System.getenv("PORT")?.toInt() ?: 8080,
            databaseUrl = System.getenv("DATABASE_URL")
                ?: "jdbc:postgresql://localhost:5432/splitup",
            databaseUser = System.getenv("DATABASE_USER") ?: "splitup",
            databasePassword = System.getenv("DATABASE_PASSWORD")
                ?: error("DATABASE_PASSWORD is required"),
            sessionSecret = System.getenv("SPLITUP_SESSION_SECRET")
                ?: error("SPLITUP_SESSION_SECRET is required (openssl rand -base64 32 | tr +/ -_ | tr -d =)"),
            openExchangeRatesApiKey = System.getenv("OPEN_EXCHANGE_RATES_API_KEY"),
            behindProxy = System.getenv("SPLITUP_BEHIND_PROXY").toBoolean(),
        )
    }
}
