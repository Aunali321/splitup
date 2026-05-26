package app.splitup.shared.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A direct payment from one person to another that reduces their net debt.
 * Modelled as a payment-type Expense in storage, but exposed as its own value
 * object so the UI can render "X paid Y $Z" cleanly.
 */
@Serializable
data class Settlement(
    val id: SettlementId,
    val groupId: GroupId?,
    val fromPersonId: PersonId,
    val toPersonId: PersonId,
    val amount: Money,
    val date: LocalDate,
    val method: SettlementMethod = SettlementMethod.UNSPECIFIED,
    val notes: String? = null,
    val externalSource: ExternalSource? = null,
    val externalId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    val currency: Currency get() = amount.currency
    val currencyCode: String get() = amount.currency.code

    init {
        require(fromPersonId != toPersonId) { "Settlement endpoints must differ" }
        require(amount.isPositive) { "Settlement amount must be positive" }
    }
}

@Serializable
enum class SettlementMethod { UNSPECIFIED, CASH, BANK_TRANSFER, PAYPAL, VENMO, UPI, SPLITWISE_PAY, OTHER }
