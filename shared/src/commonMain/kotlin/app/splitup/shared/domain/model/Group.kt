package app.splitup.shared.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val id: GroupId,
    val name: String,
    val type: GroupType = GroupType.OTHER,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val defaultCurrencyCode: String = "USD",
    val members: List<GroupMember> = emptyList(),
    val simplifyByDefault: Boolean = true,
    val whiteboard: String? = null,
    val externalSource: ExternalSource? = null,
    val externalId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
) {
    val isArchived: Boolean get() = archivedAt != null
}

@Serializable
enum class GroupType { HOME, TRIP, COUPLE, APARTMENT, HOUSE, EVENT, PROJECT, OTHER }

@Serializable
data class GroupMember(
    val personId: PersonId,
    val role: GroupRole = GroupRole.MEMBER,
    val joinedAt: Instant,
)

@Serializable
enum class GroupRole { OWNER, ADMIN, MEMBER }
