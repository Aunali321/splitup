package app.splitup.server.api

import app.splitup.server.db.CommentTable
import app.splitup.server.db.ExpenseShareTable
import app.splitup.server.db.ExpenseTable
import app.splitup.server.db.GroupMemberTable
import app.splitup.server.db.GroupTable
import app.splitup.server.db.PersonClaimTable
import app.splitup.server.db.PersonTable
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.UUID

/**
 * Applies a batch of client writes.
 *
 * Rows are shared, so nothing here trusts the client: every op is authorized
 * against group membership or expense participation before it is applied.
 *
 * The client's triggers emit whole rows rather than PowerSync's default partial
 * PATCH, so an op is either a full-row PUT or a DELETE. That keeps the payload
 * typed (a partial update cannot distinguish an absent column from an explicit
 * null) and gives last-write-wins exactly the updated_at it needs.
 */

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class SyncWriteRequest(val ops: List<SyncOp>)

@Serializable
data class SyncWriteResponse(val applied: Int, val rejected: List<Rejection>)

@Serializable
data class Rejection(val table: SyncTable, val id: String, val reason: String)

@Serializable
data class SyncOp(
    val op: OpKind,
    val table: SyncTable,
    val id: String,
    /** Absent for [OpKind.DELETE]; decoded into the table's row model otherwise. */
    val row: JsonObject? = null,
)

@Serializable
enum class OpKind { PUT, DELETE }

@Serializable
enum class SyncTable { PERSON, GROUP, GROUP_MEMBER, EXPENSE, EXPENSE_SHARE, COMMENT }

/** SQLite has no boolean type, so the client's triggers emit 0/1. */
object BoolAsInt : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("BoolAsInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeInt(if (value) 1 else 0)
    override fun deserialize(decoder: Decoder): Boolean = decoder.decodeInt() != 0
}

@Serializable
data class PersonRow(
    val id: String,
    val account_id: String? = null,
    val first_name: String,
    val last_name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val avatar_url: String? = null,
    val default_currency_code: String,
    val country_code: String? = null,
    @Serializable(with = BoolAsInt::class) val is_registered: Boolean,
    val external_source: String? = null,
    val external_id: String? = null,
    val updated_at: Instant,
    val deleted_at: Instant? = null,
)

@Serializable
data class GroupRow(
    val id: String,
    val name: String,
    val type: String,
    val avatar_url: String? = null,
    val cover_url: String? = null,
    val default_currency_code: String,
    @Serializable(with = BoolAsInt::class) val simplify_by_default: Boolean,
    val default_split_json: String? = null,
    val whiteboard: String? = null,
    val external_source: String? = null,
    val external_id: String? = null,
    val created_at: Instant,
    val updated_at: Instant,
    val archived_at: Instant? = null,
)

@Serializable
data class GroupMemberRow(
    val id: String,
    val group_id: String,
    val person_id: String,
    val role: String,
    val joined_at: Instant,
)

@Serializable
data class ExpenseRow(
    val id: String,
    val group_id: String? = null,
    val description: String,
    val cost_minor_units: Long,
    val currency_code: String,
    val date: LocalDate,
    val category_id: String,
    val notes: String? = null,
    val created_by: String,
    val split_strategy_json: String,
    @Serializable(with = BoolAsInt::class) val is_payment: Boolean,
    @Serializable(with = BoolAsInt::class) val is_refund: Boolean,
    val receipt_url: String? = null,
    val repeat_interval: String,
    val next_repeat_at: Instant? = null,
    val bundle_id: String? = null,
    val external_source: String? = null,
    val external_id: String? = null,
    val created_at: Instant,
    val updated_at: Instant,
    val deleted_at: Instant? = null,
)

@Serializable
data class ExpenseShareRow(
    val id: String,
    val expense_id: String,
    val person_id: String,
    val paid_minor_units: Long,
    val owed_minor_units: Long,
)

@Serializable
data class CommentRow(
    val id: String,
    val expense_id: String,
    val author_id: String,
    val content: String,
    val created_at: Instant,
    val updated_at: Instant,
    val deleted_at: Instant? = null,
)

/**
 * Applies [ops] in order inside the caller's transaction, each op inside its own
 * savepoint so a failing statement rolls back that op alone instead of aborting
 * the batch (Postgres poisons the whole transaction after any error otherwise).
 * Returns the ops that were refused; the endpoint reports them without failing
 * the request, because a 4xx would wedge the client's upload queue permanently.
 */
fun applyBatch(accountId: String, ops: List<SyncOp>): List<Rejection> {
    val me = myPersonId(accountId)
    val rejected = mutableListOf<Rejection>()
    ops.forEach { op ->
        val reason = runCatching { transaction { apply(op, me) } }
            .getOrElse { "malformed: ${it.message}" }
        if (reason != null) rejected += Rejection(op.table, op.id, reason)
    }
    return rejected
}

/** Returns null when applied, or the reason it was refused. */
private fun apply(op: SyncOp, me: String?): String? {
    me ?: return "no person for this account"
    return when (op.table) {
        SyncTable.PERSON -> applyPerson(op, me)
        SyncTable.GROUP -> applyGroup(op, me)
        SyncTable.GROUP_MEMBER -> applyGroupMember(op, me)
        SyncTable.EXPENSE -> applyExpense(op, me)
        SyncTable.EXPENSE_SHARE -> applyExpenseShare(op, me)
        SyncTable.COMMENT -> applyComment(op, me)
    }
}

private inline fun <reified T> SyncOp.decode(): T =
    json.decodeFromJsonElement(serializer<T>(), requireNotNull(row) { "PUT without a row" })

private fun applyPerson(op: SyncOp, me: String): String? {
    val existing = PersonTable.selectAll().where { PersonTable.id eq op.id }.firstOrNull()

    // Clients soft-delete people via PUT with deleted_at; a hard DELETE would
    // cascade through everyone's expense shares, so it is never accepted.
    if (op.op == OpKind.DELETE) {
        return if (existing == null) null else "person rows are soft-deleted, never removed"
    }

    val r = op.decode<PersonRow>()
    if (existing != null) {
        val claimedBy = existing[PersonTable.accountId]
        if (claimedBy != null && op.id != me) return "person belongs to another account"
        if (op.id != me && !canSeePerson(me, op.id)) return "person is not visible to you"
        if (existing[PersonTable.updatedAt] > r.updated_at) return null
    }

    PersonTable.upsert {
        it[id] = r.id
        // A client may not hand itself another account's identity.
        it[accountId] = existing?.get(accountId)
        it[firstName] = r.first_name
        it[lastName] = r.last_name
        it[email] = r.email
        it[phone] = r.phone
        it[avatarUrl] = r.avatar_url
        it[defaultCurrencyCode] = r.default_currency_code
        it[countryCode] = r.country_code
        it[isRegistered] = r.is_registered
        it[externalSource] = r.external_source
        it[externalId] = r.external_id
        it[updatedAt] = r.updated_at
        it[deletedAt] = r.deleted_at
    }
    return null
}

private fun applyGroup(op: SyncOp, me: String): String? {
    val exists = GroupTable.selectAll().where { GroupTable.id eq op.id }.any()
    // A brand new group has no members yet, so creation is allowed; once it exists,
    // only its members may touch it.
    if (exists && !isMember(me, op.id)) return "not a member of this group"

    if (op.op == OpKind.DELETE) {
        GroupTable.deleteWhere { id eq op.id }
        return null
    }

    val r = op.decode<GroupRow>()
    val existing = GroupTable.selectAll().where { GroupTable.id eq op.id }.firstOrNull()
    if (existing != null && existing[GroupTable.updatedAt] > r.updated_at) return null

    GroupTable.upsert {
        it[id] = r.id
        it[name] = r.name
        it[type] = r.type
        it[avatarUrl] = r.avatar_url
        it[coverUrl] = r.cover_url
        it[defaultCurrencyCode] = r.default_currency_code
        it[simplifyByDefault] = r.simplify_by_default
        it[defaultSplitJson] = r.default_split_json
        it[whiteboard] = r.whiteboard
        it[externalSource] = r.external_source
        it[externalId] = r.external_id
        it[createdAt] = r.created_at
        it[updatedAt] = r.updated_at
        it[archivedAt] = r.archived_at
    }
    return null
}

private fun applyGroupMember(op: SyncOp, me: String): String? {
    val row = if (op.op == OpKind.PUT) op.decode<GroupMemberRow>() else null
    val groupId = row?.group_id
        ?: GroupMemberTable.selectAll().where { GroupMemberTable.id eq op.id }
            .firstOrNull()?.get(GroupMemberTable.groupId)
        ?: return null

    // Bootstrapping: the creator's own first membership row establishes the group.
    // Restricting it to self keeps an emptied-out group from being taken over.
    val bootstrapping = !GroupMemberTable.selectAll()
        .where { GroupMemberTable.groupId eq groupId }.any()
    if (bootstrapping) {
        if (row != null && row.person_id != me) return "only your own membership can create a group"
    } else if (!isMember(me, groupId)) {
        return "not a member of this group"
    }

    if (op.op == OpKind.DELETE) {
        GroupMemberTable.deleteWhere { id eq op.id }
        return null
    }

    GroupMemberTable.upsert {
        it[id] = row!!.id
        it[GroupMemberTable.groupId] = row.group_id
        it[personId] = row.person_id
        it[role] = row.role
        it[joinedAt] = row.joined_at
    }
    return null
}

private fun applyExpense(op: SyncOp, me: String): String? {
    if (op.op == OpKind.DELETE) {
        if (!canWriteExpense(me, op.id)) return "cannot write this expense"
        ExpenseTable.deleteWhere { id eq op.id }
        return null
    }

    val r = op.decode<ExpenseRow>()
    if (r.group_id != null && !isMember(me, r.group_id)) return "not a member of the target group"
    val existing = ExpenseTable.selectAll().where { ExpenseTable.id eq op.id }.firstOrNull()
    if (existing != null) {
        if (!canWriteExpense(me, op.id)) return "cannot write this expense"
        if (existing[ExpenseTable.updatedAt] > r.updated_at) return null
    }

    ExpenseTable.upsert {
        it[id] = r.id
        it[groupId] = r.group_id
        it[description] = r.description
        it[costMinorUnits] = r.cost_minor_units
        it[currencyCode] = r.currency_code
        it[date] = r.date
        it[categoryId] = r.category_id
        it[notes] = r.notes
        it[createdBy] = r.created_by
        it[splitStrategyJson] = r.split_strategy_json
        it[isPayment] = r.is_payment
        it[isRefund] = r.is_refund
        it[receiptUrl] = r.receipt_url
        it[repeatInterval] = r.repeat_interval
        it[nextRepeatAt] = r.next_repeat_at
        it[bundleId] = r.bundle_id
        it[externalSource] = r.external_source
        it[externalId] = r.external_id
        it[createdAt] = r.created_at
        it[updatedAt] = r.updated_at
        it[deletedAt] = r.deleted_at
    }
    return null
}

private fun applyExpenseShare(op: SyncOp, me: String): String? {
    val expenseId = when (op.op) {
        OpKind.DELETE -> ExpenseShareTable.selectAll().where { ExpenseShareTable.id eq op.id }
            .firstOrNull()?.get(ExpenseShareTable.expenseId) ?: return null
        OpKind.PUT -> op.decode<ExpenseShareRow>().expense_id
    }
    if (!canWriteExpense(me, expenseId)) return "cannot write this expense"

    if (op.op == OpKind.DELETE) {
        ExpenseShareTable.deleteWhere { id eq op.id }
        return null
    }

    val r = op.decode<ExpenseShareRow>()
    ExpenseShareTable.upsert {
        it[id] = r.id
        it[ExpenseShareTable.expenseId] = r.expense_id
        it[personId] = r.person_id
        it[paidMinorUnits] = r.paid_minor_units
        it[owedMinorUnits] = r.owed_minor_units
    }
    return null
}

private fun applyComment(op: SyncOp, me: String): String? {
    val existing = CommentTable.selectAll().where { CommentTable.id eq op.id }.firstOrNull()
    if (existing != null && existing[CommentTable.authorId] != me) return "not your comment"

    if (op.op == OpKind.DELETE) {
        CommentTable.deleteWhere { id eq op.id }
        return null
    }

    val r = op.decode<CommentRow>()
    if (r.author_id != me) return "not your comment"
    if (!canWriteExpense(me, r.expense_id)) return "cannot see this expense"
    if (existing != null && existing[CommentTable.updatedAt] > r.updated_at) return null

    CommentTable.upsert {
        it[id] = r.id
        it[CommentTable.expenseId] = r.expense_id
        it[authorId] = r.author_id
        it[content] = r.content
        it[createdAt] = r.created_at
        it[updatedAt] = r.updated_at
        it[deletedAt] = r.deleted_at
    }
    return null
}

/**
 * Links an account to the person it *is*, upserting that person in the same call.
 *
 * Sign-in has to break a deadlock: /sync/write authorizes against the caller's person,
 * but the person itself arrives over sync. Taking the whole row here settles both at
 * once and stays idempotent. Returns the canonical person id, which differs from the
 * requested one when the account was already linked — the client re-points to it —
 * or null when the requested person belongs to somebody else.
 */
fun claimIdentity(accountId: String, r: PersonRow): String? {
    val existingForAccount = PersonTable.selectAll()
        .where { PersonTable.accountId eq accountId }
        .firstOrNull()?.get(PersonTable.id)
    if (existingForAccount != null) return existingForAccount

    val claimedByOther = PersonTable.selectAll()
        .where { PersonTable.id eq r.id }
        .firstOrNull()?.get(PersonTable.accountId)
        ?.let { it != accountId } == true
    if (claimedByOther) return null

    PersonTable.upsert {
        it[id] = r.id
        it[PersonTable.accountId] = accountId
        it[firstName] = r.first_name
        it[lastName] = r.last_name
        it[email] = r.email
        it[phone] = r.phone
        it[avatarUrl] = r.avatar_url
        it[defaultCurrencyCode] = r.default_currency_code
        it[countryCode] = r.country_code
        it[isRegistered] = true
        it[externalSource] = r.external_source
        it[externalId] = r.external_id
        it[updatedAt] = r.updated_at
        it[deletedAt] = null
    }
    return r.id
}

data class InviteOutcome(val personId: String, val claimToken: String?)

/**
 * Adds someone to a group by email, resolving them to a canonical person.
 *
 * Sharing is deliberately an online operation. Ghost friends can be created offline
 * all day, but making one visible to another account needs the server to decide which
 * person row everyone converges on — otherwise two devices invent two people for the
 * same human and the balances never line up. The caller re-points its local rows at
 * the returned id.
 *
 * When the person is unclaimed, a claim token is issued for the invitee to redeem at
 * sign-in — an email address is never proof of identity on its own. The token is only
 * handed out while the inviter is a member of every group the person belongs to, so
 * it can never grant its holder more than the inviter can already see.
 *
 * Returns null when the caller is not a member of the group.
 */
fun inviteToGroup(accountId: String, groupId: String, email: String, fallbackPersonId: String, now: Instant): InviteOutcome? {
    val me = myPersonId(accountId) ?: return null
    if (!isMember(me, groupId)) return null

    val normalized = email.lowercase()
    // A registered person wins over an unclaimed ghost with the same address.
    val canonical = PersonTable.selectAll()
        .where { PersonTable.email.lowerCase() eq normalized }
        .orderBy(PersonTable.accountId to SortOrder.DESC_NULLS_LAST)
        .firstOrNull()?.get(PersonTable.id)
        ?: fallbackPersonId.also { newId ->
            PersonTable.insert {
                it[id] = newId
                it[PersonTable.email] = normalized
                it[firstName] = email.substringBefore('@')
                it[defaultCurrencyCode] = "USD"
                it[isRegistered] = false
                it[updatedAt] = now
            }
        }

    GroupMemberTable.upsert {
        it[id] = "$groupId:$canonical"
        it[GroupMemberTable.groupId] = groupId
        it[personId] = canonical
        it[role] = "MEMBER"
        it[joinedAt] = now
    }
    return InviteOutcome(canonical, issueClaimToken(me, canonical, now))
}

private fun issueClaimToken(inviter: String, personId: String, now: Instant): String? {
    val unclaimed = PersonTable.selectAll()
        .where { (PersonTable.id eq personId) and PersonTable.accountId.isNull() }
        .any()
    if (!unclaimed) return null

    val personGroups = GroupMemberTable.select(GroupMemberTable.groupId)
        .where { GroupMemberTable.personId eq personId }
        .map { it[GroupMemberTable.groupId] }
        .toSet()
    val inviterGroups = GroupMemberTable.select(GroupMemberTable.groupId)
        .where { GroupMemberTable.personId eq inviter }
        .map { it[GroupMemberTable.groupId] }
        .toSet()
    if ((personGroups - inviterGroups).isNotEmpty()) return null

    val existing = PersonClaimTable.selectAll()
        .where { PersonClaimTable.personId eq personId }
        .firstOrNull()?.get(PersonClaimTable.token)
    if (existing != null) return existing

    val token = UUID.randomUUID().toString()
    PersonClaimTable.insert {
        it[PersonClaimTable.token] = token
        it[PersonClaimTable.personId] = personId
        it[createdAt] = now
    }
    return token
}

/**
 * Redeems an invite token: links the account to the invited person, or — when the
 * account already has a person of its own — folds the invited ghost into it so
 * memberships and balances land on the account's canonical identity. Returns the
 * canonical person id, or null when the token is unknown or already spent.
 */
fun claimInvite(accountId: String, token: String, now: Instant): String? {
    val claim = PersonClaimTable.selectAll()
        .where { PersonClaimTable.token eq token }
        .firstOrNull() ?: return null
    val invited = claim[PersonClaimTable.personId]
    PersonClaimTable.deleteWhere { PersonClaimTable.token eq token }

    val claimedBy = PersonTable.selectAll()
        .where { PersonTable.id eq invited }
        .firstOrNull()?.get(PersonTable.accountId)
    if (claimedBy != null) return if (claimedBy == accountId) invited else null

    val mine = myPersonId(accountId)
    if (mine == null || mine == invited) {
        PersonTable.update({ PersonTable.id eq invited }) {
            it[PersonTable.accountId] = accountId
            it[isRegistered] = true
            it[updatedAt] = now
        }
        return invited
    }

    mergePerson(from = invited, into = mine)
    return mine
}

/** Re-points every reference from one person to another and drops the old row. */
private fun mergePerson(from: String, into: String) {
    GroupMemberTable.selectAll().where { GroupMemberTable.personId eq from }.toList().forEach { row ->
        val groupId = row[GroupMemberTable.groupId]
        GroupMemberTable.deleteWhere { id eq row[GroupMemberTable.id] }
        val already = GroupMemberTable.selectAll()
            .where { (GroupMemberTable.groupId eq groupId) and (GroupMemberTable.personId eq into) }
            .any()
        if (!already) {
            GroupMemberTable.insert {
                it[id] = "$groupId:$into"
                it[GroupMemberTable.groupId] = groupId
                it[personId] = into
                it[role] = row[GroupMemberTable.role]
                it[joinedAt] = row[GroupMemberTable.joinedAt]
            }
        }
    }
    ExpenseShareTable.selectAll().where { ExpenseShareTable.personId eq from }.toList().forEach { row ->
        val expenseId = row[ExpenseShareTable.expenseId]
        ExpenseShareTable.deleteWhere { id eq row[ExpenseShareTable.id] }
        val already = ExpenseShareTable.selectAll()
            .where { (ExpenseShareTable.expenseId eq expenseId) and (ExpenseShareTable.personId eq into) }
            .any()
        if (!already) {
            ExpenseShareTable.insert {
                it[id] = "$expenseId:$into"
                it[ExpenseShareTable.expenseId] = expenseId
                it[personId] = into
                it[paidMinorUnits] = row[ExpenseShareTable.paidMinorUnits]
                it[owedMinorUnits] = row[ExpenseShareTable.owedMinorUnits]
            }
        }
    }
    ExpenseTable.update({ ExpenseTable.createdBy eq from }) { it[createdBy] = into }
    CommentTable.update({ CommentTable.authorId eq from }) { it[authorId] = into }
    PersonTable.deleteWhere { id eq from }
}

private fun myPersonId(accountId: String): String? = PersonTable.selectAll()
    .where { PersonTable.accountId eq accountId }
    .firstOrNull()?.get(PersonTable.id)

private fun isMember(personId: String, groupId: String): Boolean = GroupMemberTable.selectAll()
    .where { (GroupMemberTable.groupId eq groupId) and (GroupMemberTable.personId eq personId) }
    .any()

/** Visible means sharing a group or an expense with the caller. */
private fun canSeePerson(me: String, personId: String): Boolean {
    val myGroups = GroupMemberTable.select(GroupMemberTable.groupId)
        .where { GroupMemberTable.personId eq me }
    val sharesGroup = GroupMemberTable.selectAll()
        .where { (GroupMemberTable.personId eq personId) and (GroupMemberTable.groupId inSubQuery myGroups) }
        .any()
    if (sharesGroup) return true

    val myExpenses = ExpenseShareTable.select(ExpenseShareTable.expenseId)
        .where { ExpenseShareTable.personId eq me }
    return ExpenseShareTable.selectAll()
        .where { (ExpenseShareTable.personId eq personId) and (ExpenseShareTable.expenseId inSubQuery myExpenses) }
        .any()
}

/**
 * A group expense is writable by its members; a non-group one by its participants.
 * The author counts too, because the share that would make them a participant is
 * itself one of the writes being authorized.
 */
private fun canWriteExpense(me: String, expenseId: String): Boolean {
    val row = ExpenseTable.selectAll().where { ExpenseTable.id eq expenseId }.firstOrNull()
        ?: return false
    row[ExpenseTable.groupId]?.let { return isMember(me, it) }
    if (row[ExpenseTable.createdBy] == me) return true
    return ExpenseShareTable.selectAll()
        .where { (ExpenseShareTable.expenseId eq expenseId) and (ExpenseShareTable.personId eq me) }
        .any()
}
