package app.splitup.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDb
import javax.sql.DataSource

class Database private constructor(
    val source: DataSource,
    val exposed: ExposedDb,
) {
    fun migrate() {
        Flyway.configure()
            .dataSource(source)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    companion object {
        fun connect(url: String, user: String, password: String): Database {
            val hikari = HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = 10
                isAutoCommit = false
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
            }
            val ds = HikariDataSource(hikari)
            // Nested transactions become savepoints, which /sync/write relies on to
            // reject a single op without aborting the whole batch's transaction.
            val exposed = ExposedDb.connect(
                datasource = ds,
                databaseConfig = DatabaseConfig { useNestedTransactions = true },
            )
            return Database(ds, exposed)
        }
    }
}
