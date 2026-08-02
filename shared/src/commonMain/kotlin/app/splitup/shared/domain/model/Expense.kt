package app.splitup.shared.domain.model

import app.splitup.shared.domain.split.SplitStrategy
import kotlinx.datetime.DatePeriod
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * Invariant enforced on construction:
 *   shares.sumOf { paid } == cost
 *   shares.sumOf { owed } == cost
 * Per-person `paid - owed` is what the rest of the group owes them.
 */
@Serializable
data class Expense(
    val id: ExpenseId,
    val groupId: GroupId?,
    val description: String,
    val cost: Money,
    val date: LocalDate,
    val categoryId: CategoryId = CategoryId("uncategorized"),
    val notes: String? = null,
    val createdBy: PersonId,
    val splitStrategy: SplitStrategy,
    val shares: List<ExpenseShare>,
    val isPayment: Boolean = false,
    val isRefund: Boolean = false,
    val receiptUrl: String? = null,
    val repeatInterval: RepeatInterval = RepeatInterval.NEVER,
    val nextRepeatAt: Instant? = null,
    val bundleId: String? = null,
    val externalSource: ExternalSource? = null,
    val externalId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    val currency: Currency get() = cost.currency
    val currencyCode: String get() = cost.currency.code

    init {
        require(description.isNotBlank()) { "Expense description is required" }
        require(cost.isPositive || isPayment || isRefund) { "Expense cost must be positive" }
        require(shares.isNotEmpty()) { "Expense must have at least one share" }
        require(shares.map { it.personId }.toSet().size == shares.size) {
            "Duplicate participants in shares: ${shares.map { it.personId }}"
        }
        require(shares.all { it.paidShare.currency == cost.currency && it.owedShare.currency == cost.currency }) {
            "All share amounts must be in ${cost.currency.code}"
        }
        val zero = Money.zero(cost.currency)
        val paid = shares.fold(zero) { acc, s -> acc + s.paidShare }
        val owed = shares.fold(zero) { acc, s -> acc + s.owedShare }
        require(paid == cost) { "Paid shares ($paid) must sum to cost ($cost)" }
        require(owed == cost) { "Owed shares ($owed) must sum to cost ($cost)" }
    }

    val isDeleted: Boolean get() = deletedAt != null

    fun balanceFor(person: PersonId): Money =
        shares.firstOrNull { it.personId == person }?.let { it.paidShare - it.owedShare }
            ?: Money.zero(cost.currency)
}

@Serializable
data class ExpenseShare(
    val personId: PersonId,
    val paidShare: Money,
    val owedShare: Money,
)

@Serializable
enum class RepeatInterval { NEVER, DAILY, WEEKLY, FORTNIGHTLY, MONTHLY, YEARLY }

/** The occurrence after [from]. Only meaningful for real intervals. */
fun RepeatInterval.next(from: LocalDate): LocalDate = from.plus(
    when (this) {
        RepeatInterval.NEVER -> throw IllegalArgumentException("NEVER does not recur")
        RepeatInterval.DAILY -> DatePeriod(days = 1)
        RepeatInterval.WEEKLY -> DatePeriod(days = 7)
        RepeatInterval.FORTNIGHTLY -> DatePeriod(days = 14)
        RepeatInterval.MONTHLY -> DatePeriod(months = 1)
        RepeatInterval.YEARLY -> DatePeriod(years = 1)
    },
)
