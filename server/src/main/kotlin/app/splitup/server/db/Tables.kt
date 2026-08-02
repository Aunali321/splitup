package app.splitup.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * Exposed table definitions.
 *
 * Keep these aligned with the latest Flyway migration; the migrations are the source of truth.
 * Everything except [AccountTable] and [SessionTable] is replicated to clients by
 * PowerSync and written back through the authorizing /sync/write endpoint.
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

object SessionTable : Table("session") {
    val id = text("id")
    val accountId = text("account_id")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(id)
}

/** [accountId] names the account this person *is*; null for unregistered friends. */
object PersonTable : Table("person") {
    val id = text("id")
    val accountId = text("account_id").nullable()
    val firstName = text("first_name")
    val lastName = text("last_name").nullable()
    val email = text("email").nullable()
    val phone = text("phone").nullable()
    val avatarUrl = text("avatar_url").nullable()
    val defaultCurrencyCode = text("default_currency_code")
    val countryCode = text("country_code").nullable()
    val isRegistered = bool("is_registered")
    val externalSource = text("external_source").nullable()
    val externalId = text("external_id").nullable()
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object GroupTable : Table("group_") {
    val id = text("id")
    val name = text("name")
    val type = text("type")
    val avatarUrl = text("avatar_url").nullable()
    val coverUrl = text("cover_url").nullable()
    val defaultCurrencyCode = text("default_currency_code")
    val simplifyByDefault = bool("simplify_by_default")
    val defaultSplitJson = text("default_split_json").nullable()
    val whiteboard = text("whiteboard").nullable()
    val externalSource = text("external_source").nullable()
    val externalId = text("external_id").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val archivedAt = timestamp("archived_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** The access-control table: membership decides who syncs a group's content. */
object GroupMemberTable : Table("group_member") {
    val id = text("id")
    val groupId = text("group_id")
    val personId = text("person_id")
    val role = text("role")
    val joinedAt = timestamp("joined_at")
    override val primaryKey = PrimaryKey(id)
}

object ExpenseTable : Table("expense") {
    val id = text("id")
    val groupId = text("group_id").nullable()
    val description = text("description")
    val costMinorUnits = long("cost_minor_units")
    val currencyCode = text("currency_code")
    val date = date("date")
    val categoryId = text("category_id")
    val notes = text("notes").nullable()
    val createdBy = text("created_by")
    val splitStrategyJson = text("split_strategy_json")
    val isPayment = bool("is_payment")
    val isRefund = bool("is_refund")
    val receiptUrl = text("receipt_url").nullable()
    val repeatInterval = text("repeat_interval")
    val nextRepeatAt = timestamp("next_repeat_at").nullable()
    val bundleId = text("bundle_id").nullable()
    val externalSource = text("external_source").nullable()
    val externalId = text("external_id").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object ExpenseShareTable : Table("expense_share") {
    val id = text("id")
    val expenseId = text("expense_id")
    val personId = text("person_id")
    val paidMinorUnits = long("paid_minor_units")
    val owedMinorUnits = long("owed_minor_units")
    override val primaryKey = PrimaryKey(id)
}

object CommentTable : Table("comment") {
    val id = text("id")
    val expenseId = text("expense_id")
    val authorId = text("author_id")
    val content = text("content")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Bearer capability to become an unclaimed person: the invitee redeems the token
 * at sign-in and their account is linked (or merged) to that person. Never
 * replicated — the token must not leak to group members via sync.
 */
object PersonClaimTable : Table("person_claim") {
    val token = text("token")
    val personId = text("person_id")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(token)
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
