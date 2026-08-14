package app.splitup.shared.splitwise

import app.splitup.shared.domain.model.Comment
import app.splitup.shared.domain.model.CommentId
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.DefaultCategories
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.ExpenseShare
import app.splitup.shared.domain.model.ExternalSource
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.GroupMember
import app.splitup.shared.domain.model.GroupRole
import app.splitup.shared.domain.model.GroupType
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.repository.CommentRepository
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.repository.TransactionRunner
import app.splitup.shared.domain.split.SplitStrategy
import app.splitup.shared.util.IdGenerator
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Pulls a user's full Splitwise account into the local database. Idempotent — repeated
 * runs detect already-imported rows by (external_source, external_id) and update
 * them in place. All network fetching happens first; persistence then runs in a
 * single transaction so a failed import leaves no partial state behind.
 *
 * Maps Splitwise's `group_id == 0` and `null` to a domain `groupId = null` (non-group
 * expenses between friends).
 */
class SplitwiseImporter(
    private val engineProvider: () -> HttpClientEngine,
    private val transactions: TransactionRunner,
    private val people: PersonRepository,
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val comments: CommentRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    data class Result(
        val peopleCount: Int,
        val groupCount: Int,
        val expenseCount: Int,
        val commentCount: Int = 0,
    )

    suspend fun run(
        apiKey: String,
        onProgress: (phase: String, progress: Float) -> Unit = { _, _ -> },
    ): Result {
        val client = SplitwiseClient(engineProvider(), apiKey)
        try {
            onProgress("Authenticating", 0.02f)
            val me = client.getCurrentUser()

            onProgress("Fetching friends", 0.10f)
            val friends = client.getFriends()

            onProgress("Fetching groups", 0.20f)
            val swGroups = client.getGroups()

            onProgress("Fetching expenses", 0.30f)
            val allExpenses = ArrayList<SwExpense>()
            var offset = 0
            val pageSize = 100
            while (true) {
                val page = client.getExpensesPage(limit = pageSize, offset = offset)
                if (page.isEmpty()) break
                allExpenses += page
                offset += page.size
                val pct = 0.3f + (offset.coerceAtMost(2000) / 2000f) * 0.55f
                onProgress("Fetched ${allExpenses.size} expenses", pct)
                if (page.size < pageSize) break
            }

            onProgress("Fetching comments", 0.85f)
            // One call per commented expense; Splitwise has no bulk endpoint.
            val commentsByExpense: Map<Long, List<SwComment>> = coroutineScope {
                val gate = Semaphore(8)
                allExpenses.filter { it.comments_count > 0 }
                    .map { e -> async { gate.withPermit { e.id to client.getComments(e.id) } } }
                    .awaitAll()
                    .toMap()
            }

            onProgress("Saving", 0.9f)
            val result = transactions.inTransaction {
                val personIdByExternal = HashMap<Long, PersonId>()
                personIdByExternal[me.id] = upsertPerson(me, isMe = true)
                friends.forEach { f ->
                    personIdByExternal.getOrPut(f.id) { upsertPerson(f.toSwUser(), isMe = false) }
                }
                val groupIdByExternal = HashMap<Long, GroupId>()
                swGroups.forEach { g ->
                    g.members.forEach { m ->
                        personIdByExternal.getOrPut(m.id) { upsertPerson(m.toSwUser(), isMe = false) }
                    }
                    // Splitwise's id-0 pseudo-group is their "Non-group expenses" bucket;
                    // those expenses map to groupId = null, not a real group.
                    if (g.id != 0L) {
                        groupIdByExternal[g.id] = upsertGroup(g, personIdByExternal)
                    }
                }
                val myId = personIdByExternal.getValue(me.id)
                val expenseIdByExternal = HashMap<Long, ExpenseId>()
                allExpenses.forEach { e ->
                    e.users.forEach { u ->
                        personIdByExternal.getOrPut(u.user_id) { upsertPerson(u.user, isMe = false) }
                    }
                    upsertExpense(e, groupIdByExternal, personIdByExternal, myId)
                        ?.let { expenseIdByExternal[e.id] = it }
                }
                var commentCount = 0
                commentsByExpense.forEach { (swExpenseId, swComments) ->
                    val expenseId = expenseIdByExternal[swExpenseId] ?: return@forEach
                    swComments.forEach { c ->
                        if (upsertComment(c, expenseId, personIdByExternal)) commentCount++
                    }
                }
                Result(
                    peopleCount = personIdByExternal.size,
                    groupCount = groupIdByExternal.size,
                    expenseCount = allExpenses.size,
                    commentCount = commentCount,
                )
            }

            onProgress("Done", 1.0f)
            return result
        } finally {
            client.close()
        }
    }

    private suspend fun upsertPerson(u: SwUser, isMe: Boolean): PersonId {
        // For "me": prefer the onboarding row (already isMe=true) over creating a
        // second one. The Splitwise external_id then attaches to that row so
        // expense shares — which reference this UUID — line up with state.me.
        val existing = if (isMe) {
            people.findByExternal(ExternalSource.SPLITWISE.name, u.id.toString())
                ?: people.getMe()
        } else {
            people.findByExternal(ExternalSource.SPLITWISE.name, u.id.toString())
        }
        val id = existing?.id ?: PersonId(idGenerator.next())
        val person = Person(
            id = id,
            firstName = existing?.firstName?.takeIf { it.isNotBlank() } ?: u.first_name ?: "User${u.id}",
            lastName = existing?.lastName ?: u.last_name,
            email = existing?.email ?: u.email,
            avatarUrl = existing?.avatarUrl ?: u.picture?.large ?: u.picture?.medium,
            defaultCurrencyCode = existing?.defaultCurrencyCode ?: u.default_currency ?: "USD",
            countryCode = existing?.countryCode ?: u.country_code,
            isMe = isMe || existing?.isMe == true,
            isRegistered = u.registration_status == "confirmed",
            externalSource = ExternalSource.SPLITWISE,
            externalId = u.id.toString(),
            updatedAt = clock.now(),
        )
        people.save(person)
        return id
    }

    private suspend fun upsertGroup(g: SwGroup, personIds: Map<Long, PersonId>): GroupId {
        val existing = groups.findByExternal(ExternalSource.SPLITWISE.name, g.id.toString())
        val id = existing?.id ?: GroupId(idGenerator.next())
        val members = g.members.mapNotNull { m ->
            personIds[m.id]?.let { pid ->
                GroupMember(personId = pid, role = GroupRole.MEMBER, joinedAt = parseInstantOrNow(g.created_at))
            }
        }
        val group = Group(
            id = id,
            name = g.name,
            type = mapGroupType(g.group_type),
            avatarUrl = existing?.avatarUrl ?: g.avatar?.large ?: g.avatar?.medium,
            coverUrl = existing?.coverUrl ?: g.cover_photo?.large ?: g.cover_photo?.medium,
            defaultCurrencyCode = existing?.defaultCurrencyCode ?: "USD",
            members = members,
            simplifyByDefault = g.simplify_by_default,
            whiteboard = g.whiteboard,
            externalSource = ExternalSource.SPLITWISE,
            externalId = g.id.toString(),
            createdAt = parseInstantOrNow(g.created_at),
            updatedAt = parseInstantOrNow(g.updated_at),
        )
        groups.save(group)
        return id
    }

    private suspend fun upsertExpense(
        e: SwExpense,
        groupIds: Map<Long, GroupId>,
        personIds: Map<Long, PersonId>,
        me: PersonId,
    ): ExpenseId? {
        val existing = expenses.findByExternalId(ExternalSource.SPLITWISE.name, e.id.toString())
        val id = existing?.id ?: ExpenseId(idGenerator.next())
        val currency = Currency.ofCodeOrDefault(e.currency_code)
        val total = Money.parse(e.cost, currency)

        val shares = e.users.mapNotNull { u ->
            personIds[u.user_id]?.let { pid ->
                ExpenseShare(
                    personId = pid,
                    paidShare = Money.parse(u.paid_share, currency),
                    owedShare = Money.parse(u.owed_share, currency),
                )
            }
        }
        if (shares.isEmpty()) return null

        // Splitwise sometimes returns expenses where shares don't perfectly sum due to rounding
        // in their backend. Adjust the largest owed share by the delta so our invariant holds.
        val owedSum = shares.fold(0L) { acc, s -> acc + s.owedShare.minorUnits }
        val paidSum = shares.fold(0L) { acc, s -> acc + s.paidShare.minorUnits }
        val balanced = balanceShares(shares, total.minorUnits, owedSum, paidSum, currency)

        val groupId = e.group_id?.takeIf { it != 0L }?.let { groupIds[it] }
        val createdBy = e.created_by?.let { personIds[it.id] } ?: me

        val expense = Expense(
            id = id,
            groupId = groupId,
            description = e.description,
            cost = total,
            date = parseDateOrToday(e.date),
            categoryId = e.category?.let { SplitwiseCategories.toLocal(it.id) } ?: DefaultCategories.UNCATEGORIZED,
            notes = e.details,
            createdBy = createdBy,
            splitStrategy = SplitStrategy.Exact(balanced.associate { it.personId to it.owedShare }),
            shares = balanced,
            isPayment = e.payment,
            receiptUrl = e.receipt?.original ?: e.receipt?.large,
            repeatInterval = mapRepeatInterval(e.repeat_interval),
            // Carrying next_repeat over lets local materialization take the
            // recurrence over after migrating off Splitwise.
            nextRepeatAt = e.next_repeat?.let { runCatching { Instant.parse(it) }.getOrNull() },
            bundleId = e.expense_bundle_id?.toString(),
            externalSource = ExternalSource.SPLITWISE,
            externalId = e.id.toString(),
            createdAt = parseInstantOrNow(e.created_at),
            updatedAt = parseInstantOrNow(e.updated_at),
            deletedAt = e.deleted_at?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
        expenses.save(expense)
        return id
    }

    /** True when a user comment was written; system comments and unknown authors are skipped. */
    private suspend fun upsertComment(
        c: SwComment,
        expenseId: ExpenseId,
        personIds: Map<Long, PersonId>,
    ): Boolean {
        if (c.commentType != null && !c.commentType.equals("User", ignoreCase = true)) return false
        val author = personIds[c.user.id] ?: return false
        val at = parseInstantOrNow(c.created_at)
        comments.add(
            Comment(
                id = CommentId("splitwise-${c.id}"),
                expenseId = expenseId,
                authorId = author,
                content = c.content,
                createdAt = at,
                updatedAt = at,
                deletedAt = c.deleted_at?.let { runCatching { Instant.parse(it) }.getOrNull() },
            ),
        )
        return true
    }

    private fun balanceShares(
        shares: List<ExpenseShare>,
        targetMinor: Long,
        owedSum: Long,
        paidSum: Long,
        currency: Currency,
    ): List<ExpenseShare> {
        if (owedSum == targetMinor && paidSum == targetMinor) return shares
        val mutable = shares.toMutableList()
        if (owedSum != targetMinor) {
            val idx = mutable.indices.maxBy { mutable[it].owedShare.minorUnits }
            val delta = targetMinor - owedSum
            mutable[idx] = mutable[idx].copy(
                owedShare = Money.ofMinor(mutable[idx].owedShare.minorUnits + delta, currency),
            )
        }
        if (paidSum != targetMinor) {
            val idx = mutable.indices.maxBy { mutable[it].paidShare.minorUnits }
            val delta = targetMinor - paidSum
            mutable[idx] = mutable[idx].copy(
                paidShare = Money.ofMinor(mutable[idx].paidShare.minorUnits + delta, currency),
            )
        }
        return mutable
    }

    private fun mapGroupType(s: String?): GroupType = when (s?.lowercase()) {
        "house", "home" -> GroupType.HOUSE
        "apartment" -> GroupType.APARTMENT
        "trip" -> GroupType.TRIP
        "couple" -> GroupType.COUPLE
        else -> GroupType.OTHER
    }

    private fun mapRepeatInterval(s: String?): RepeatInterval = when (s?.lowercase()) {
        "daily" -> RepeatInterval.DAILY
        "weekly" -> RepeatInterval.WEEKLY
        "fortnightly" -> RepeatInterval.FORTNIGHTLY
        "monthly" -> RepeatInterval.MONTHLY
        "yearly" -> RepeatInterval.YEARLY
        else -> RepeatInterval.NEVER
    }

    private fun parseInstantOrNow(s: String?): Instant =
        s?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: clock.now()

    /**
     * Splitwise stores the date as a UTC timestamp derived from the creator's local
     * date, so reading it back in UTC moves every expense a day for anyone east of
     * it. The device's own zone is the closest thing to the date the user entered.
     */
    private fun parseDateOrToday(s: String?): LocalDate {
        val i = s?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return (i ?: clock.now()).toLocalDateTime(timeZone).date
    }
}

private fun SwFriend.toSwUser() = SwUser(
    id = id, first_name = first_name, last_name = last_name, email = email,
    registration_status = registration_status, picture = picture,
)

private fun SwMember.toSwUser() = SwUser(
    id = id, first_name = first_name, last_name = last_name, email = email,
    registration_status = registration_status, picture = picture,
)
