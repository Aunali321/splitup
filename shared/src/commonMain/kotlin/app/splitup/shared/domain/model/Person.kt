package app.splitup.shared.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Person(
    val id: PersonId,
    val firstName: String,
    val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val defaultCurrencyCode: String = "USD",
    val countryCode: String? = null,
    val isMe: Boolean = false,
    /** The account this person *is*, not an owner. Null until they register. */
    val accountId: String? = null,
    val isRegistered: Boolean = false,
    val externalSource: ExternalSource? = null,
    val externalId: String? = null,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    val displayName: String get() = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { "Unknown" }
    val initials: String get() {
        val f = firstName.firstOrNull()?.uppercaseChar() ?: '?'
        val l = lastName?.firstOrNull()?.uppercaseChar()
        return if (l != null) "$f$l" else f.toString()
    }
}

@Serializable
enum class ExternalSource { SPLITWISE }
