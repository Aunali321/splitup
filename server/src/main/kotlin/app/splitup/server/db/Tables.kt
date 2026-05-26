package app.splitup.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Exposed table definitions. Only the tables the server reads/writes directly —
 * the rest of the schema is owned by PowerSync (clients write, server replicates).
 *
 * Keep these aligned with V1__init.sql; the Flyway migration is the source of truth.
 */
object AccountTable : Table("account") {
    val id = text("id")
    val email = text("email").uniqueIndex()
    val passwordHash = text("password_hash")
    val displayName = text("display_name")
    val locale = text("locale").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object UserPreferencesTable : Table("user_preferences") {
    val accountId = text("account_id")
    val homeCurrencyCode = text("home_currency_code").default("USD")
    val convertToHomeInUi = bool("convert_to_home_in_ui").default(true)
    val fxSource = text("fx_source").default("ECB")
    val locale = text("locale").nullable()
    val firstDayOfWeek = integer("first_day_of_week").default(1)
    val theme = text("theme").default("SYSTEM")
    val useDynamicColor = bool("use_dynamic_color").default(true)
    val decimalSeparator = text("decimal_separator").nullable()
    val biometricLock = bool("biometric_lock").default(false)
    val pushEnabled = bool("push_enabled").default(true)
    val onboardingCompletedAt = timestamp("onboarding_completed_at").nullable()
    override val primaryKey = PrimaryKey(accountId)
}

object ExchangeRateTable : Table("exchange_rate") {
    val fromCode = text("from_code")
    val toCode = text("to_code")
    val rate8 = long("rate8")
    val date = date("date")
    val sourceName = text("source")
    val fetchedAt = timestamp("fetched_at")
    override val primaryKey = PrimaryKey(fromCode, toCode, date)
}
