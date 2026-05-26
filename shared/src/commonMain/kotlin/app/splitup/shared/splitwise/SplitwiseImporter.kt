package app.splitup.shared.splitwise

import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.Currency
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
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.split.SplitStrategy
import app.splitup.shared.util.IdGenerator
import io.ktor.client.engine.HttpClientEngine
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Pulls a user's full Splitwise account into the local database. Idempotent — repeated
 * runs detect already-imported rows by (external_source, external_id) and update
 * them in place.
 *
 * Maps Splitwise's `group_id == 0` and `null` to a domain `groupId = null` (non-group
 * expenses between friends).
 */
class SplitwiseImporter(
    private val engineProvider: () -> HttpClientEngine,
    private val people: PersonRepository,
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock = Clock.System,
) {
    data class Result(val peopleCount: Int, val groupCount: Int, val expenseCount: Int)

    suspend fun run(
        apiKey: String,
        onProgress: (phase: String, progress: Float) -> Unit = { _, _ -> },
    ): Result {
        val client = SplitwiseClient(engineProvider(), apiKey)
        try {
            onProgress("Authenticating", 0.02f)
            val me = client.getCurrentUser()

            onProgress("Importing your profile", 0.05f)
            val personIdByExternal = HashMap<Long, PersonId>()
            personIdByExternal[me.id] = upsertPerson(me, isMe = true)

            onProgress("Importing friends", 0.15f)
            client.getFriends().forEach { f ->
                personIdByExternal.getOrPut(f.id) { upsertPerson(f.toSwUser(), isMe = false) }
            }

            onProgress("Importing groups", 0.30f)
            val groupIdByExternal = HashMap<Long, GroupId>()
            client.getGroups().forEach { g ->
                g.members.forEach { m ->
                    personIdByExternal.getOrPut(m.id) { upsertPerson(m.toSwUser(), isMe = false) }
                }
                groupIdByExternal[g.id] = upsertGroup(g, personIdByExternal)
            }

            onProgress("Importing expenses", 0.50f)
            val allExpenses = ArrayList<SwExpense>()
            var offset = 0
            val pageSize = 100
            while (true) {
                val page = client.getExpensesPage(limit = pageSize, offset = offset)
                if (page.isEmpty()) break
                allExpenses += page
                offset += page.size
                val pct = 0.5f + (offset.coerceAtMost(2000) / 2000f) * 0.40f
                onProgress("Imported ${allExpenses.size} expenses", pct)
                if (page.size < pageSize) break
            }

            onProgress("Persisting", 0.92f)
            val myId = personIdByExternal.getValue(me.id)
            allExpenses.forEach { e ->
                e.users.forEach { u ->
                    personIdByExternal.getOrPut(u.user_id) { upsertPerson(u.user, isMe = false) }
                }
                upsertExpense(e, groupIdByExternal, personIdByExternal, myId)
            }

            onProgress("Done", 1.0f)
            return Result(
                peopleCount = personIdByExternal.size,
                groupCount = groupIdByExternal.size,
                expenseCount = allExpenses.size,
            )
        } finally {
            client.close()
        }
    }

    private suspend fun upsertPerson(u: SwUser, isMe: Boolean): PersonId {
        val existing = people.findByExternal(ExternalSource.SPLITWISE.name, u.id.toString())
        val id = existing?.id ?: PersonId(idGenerator.next())
        val person = Person(
            id = id,
            firstName = u.first_name ?: "User${u.id}",
            lastName = u.last_name,
            email = u.email,
            avatarUrl = u.picture?.large ?: u.picture?.medium,
            defaultCurrencyCode = u.default_currency ?: "USD",
            countryCode = u.country_code,
            isMe = isMe,
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
            avatarUrl = g.avatar?.large ?: g.avatar?.medium,
            coverUrl = g.cover_photo?.large ?: g.cover_photo?.medium,
            defaultCurrencyCode = "USD",
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
    ) {
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
        if (shares.isEmpty()) return

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
            categoryId = e.category?.let { CategoryId(it.id.toString()) } ?: CategoryId("uncategorized"),
            notes = e.details,
            createdBy = createdBy,
            splitStrategy = SplitStrategy.Exact(balanced.associate { it.personId to it.owedShare }),
            shares = balanced,
            isPayment = e.payment,
            receiptUrl = e.receipt?.original ?: e.receipt?.large,
            repeatInterval = mapRepeatInterval(e.repeat_interval),
            bundleId = e.expense_bundle_id?.toString(),
            externalSource = ExternalSource.SPLITWISE,
            externalId = e.id.toString(),
            createdAt = parseInstantOrNow(e.created_at),
            updatedAt = parseInstantOrNow(e.updated_at),
            deletedAt = e.deleted_at?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
        expenses.save(expense)
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

    private fun parseDateOrToday(s: String?): LocalDate {
        val i = s?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return (i ?: clock.now()).toLocalDateTime(TimeZone.UTC).date
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
