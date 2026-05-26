package app.splitup.server

data class ServerConfig(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    /** 32 random bytes, base64url-encoded. Same value goes to PowerSync as the HS256 JWK. */
    val sessionSecret: String,
    val openExchangeRatesApiKey: String?,
) {
    companion object {
        fun fromEnv(): ServerConfig = ServerConfig(
            port = System.getenv("PORT")?.toInt() ?: 8080,
            databaseUrl = System.getenv("DATABASE_URL")
                ?: "jdbc:postgresql://localhost:5432/splitup",
            databaseUser = System.getenv("DATABASE_USER") ?: "splitup",
            databasePassword = System.getenv("DATABASE_PASSWORD") ?: "splitup",
            sessionSecret = System.getenv("SPLITUP_SESSION_SECRET")
                ?: error("SPLITUP_SESSION_SECRET is required (openssl rand -base64 32 | tr +/ -_ | tr -d =)"),
            openExchangeRatesApiKey = System.getenv("OPEN_EXCHANGE_RATES_API_KEY"),
        )
    }
}
