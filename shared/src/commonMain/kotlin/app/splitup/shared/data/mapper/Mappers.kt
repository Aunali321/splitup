package app.splitup.shared.data.mapper

import app.splitup.shared.data.local.entity.CommentEntity
import app.splitup.shared.data.local.entity.ExpenseEntity
import app.splitup.shared.data.local.entity.ExpenseShareEntity
import app.splitup.shared.data.local.entity.ExpenseWithShares
import app.splitup.shared.data.local.entity.GroupEntity
import app.splitup.shared.data.local.entity.GroupMemberEntity
import app.splitup.shared.data.local.entity.PersonEntity
import app.splitup.shared.data.local.entity.UserPreferencesEntity
import app.splitup.shared.data.local.entity.memberId
import app.splitup.shared.data.local.entity.shareId
import app.splitup.shared.domain.model.CategoryId
import app.splitup.shared.domain.model.Comment
import app.splitup.shared.domain.model.CommentId
import app.splitup.shared.domain.model.Currency
import app.splitup.shared.domain.model.Expense
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.ExpenseShare
import app.splitup.shared.domain.model.ExternalSource
import app.splitup.shared.domain.model.FxSource
import app.splitup.shared.domain.model.Group
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.GroupMember
import app.splitup.shared.domain.model.GroupRole
import app.splitup.shared.domain.model.GroupType
import app.splitup.shared.domain.model.Money
import app.splitup.shared.domain.model.Person
import app.splitup.shared.domain.model.PersonId
import app.splitup.shared.domain.model.RepeatInterval
import app.splitup.shared.domain.model.ThemePreference
import app.splitup.shared.domain.model.UserPreferences
import app.splitup.shared.domain.split.SplitStrategy
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun PersonEntity.toDomain() = Person(
    id = PersonId(id),
    firstName = first_name,
    lastName = last_name,
    email = email,
    phone = phone,
    avatarUrl = avatar_url,
    defaultCurrencyCode = default_currency_code,
    countryCode = country_code,
    isMe = is_me,
    accountId = account_id,
    isRegistered = is_registered,
    externalSource = external_source?.let { ExternalSource.valueOf(it) },
    externalId = external_id,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)

fun Person.toEntity() = PersonEntity(
    id = id.value,
    first_name = firstName,
    last_name = lastName,
    email = email,
    phone = phone,
    avatar_url = avatarUrl,
    default_currency_code = defaultCurrencyCode,
    country_code = countryCode,
    is_me = isMe,
    account_id = accountId,
    is_registered = isRegistered,
    external_source = externalSource?.name,
    external_id = externalId,
    updated_at = updatedAt,
    // Dropping this on the way down would resurrect a deleted person on the next
    // save, and the trigger would upload the resurrection to everyone.
    deleted_at = deletedAt,
)

fun GroupEntity.toDomain(members: List<GroupMemberEntity>) = Group(
    id = GroupId(id),
    name = name,
    type = GroupType.valueOf(type),
    avatarUrl = avatar_url,
    coverUrl = cover_url,
    defaultCurrencyCode = default_currency_code,
    members = members.map { it.toDomain() },
    simplifyByDefault = simplify_by_default,
    defaultSplit = default_split_json?.let { json.decodeFromString<SplitStrategy>(it) },
    whiteboard = whiteboard,
    externalSource = external_source?.let { ExternalSource.valueOf(it) },
    externalId = external_id,
    createdAt = created_at,
    updatedAt = updated_at,
    archivedAt = archived_at,
)

fun Group.toEntity() = GroupEntity(
    id = id.value,
    name = name,
    type = type.name,
    avatar_url = avatarUrl,
    cover_url = coverUrl,
    default_currency_code = defaultCurrencyCode,
    simplify_by_default = simplifyByDefault,
    default_split_json = defaultSplit?.let { json.encodeToString(SplitStrategy.serializer(), it) },
    whiteboard = whiteboard,
    external_source = externalSource?.name,
    external_id = externalId,
    created_at = createdAt,
    updated_at = updatedAt,
    archived_at = archivedAt,
)

fun GroupMember.toEntity(groupId: GroupId) = GroupMemberEntity(
    id = memberId(groupId.value, personId.value),
    group_id = groupId.value,
    person_id = personId.value,
    role = role.name,
    joined_at = joinedAt,
)

fun GroupMemberEntity.toDomain() = GroupMember(
    personId = PersonId(person_id),
    role = GroupRole.valueOf(role),
    joinedAt = joined_at,
)

fun ExpenseWithShares.toDomain(): Expense = expense.toDomain(shares)

private fun ExpenseEntity.toDomain(shareEntities: List<ExpenseShareEntity>): Expense {
    val currency = Currency.ofCodeOrDefault(currency_code)
    val cost = Money.ofMinor(cost_minor_units, currency)
    val shares = shareEntities.map {
        ExpenseShare(
            personId = PersonId(it.person_id),
            paidShare = Money.ofMinor(it.paid_minor_units, currency),
            owedShare = Money.ofMinor(it.owed_minor_units, currency),
        )
    }
    return Expense(
        id = ExpenseId(id),
        groupId = group_id?.let { GroupId(it) },
        description = description,
        cost = cost,
        date = date,
        categoryId = CategoryId(category_id),
        notes = notes,
        createdBy = PersonId(created_by),
        splitStrategy = json.decodeFromString<SplitStrategy>(split_strategy_json),
        shares = shares,
        isPayment = is_payment,
        isRefund = is_refund,
        receiptUrl = receipt_url,
        repeatInterval = RepeatInterval.valueOf(repeat_interval),
        nextRepeatAt = next_repeat_at,
        bundleId = bundle_id,
        externalSource = external_source?.let { ExternalSource.valueOf(it) },
        externalId = external_id,
        createdAt = created_at,
        updatedAt = updated_at,
        deletedAt = deleted_at,
    )
}

fun Expense.toEntity() = ExpenseEntity(
    id = id.value,
    group_id = groupId?.value,
    description = description,
    cost_minor_units = cost.minorUnits,
    currency_code = currency.code,
    date = date,
    category_id = categoryId.value,
    notes = notes,
    created_by = createdBy.value,
    split_strategy_json = json.encodeToString(SplitStrategy.serializer(), splitStrategy),
    is_payment = isPayment,
    is_refund = isRefund,
    receipt_url = receiptUrl,
    repeat_interval = repeatInterval.name,
    next_repeat_at = nextRepeatAt,
    bundle_id = bundleId,
    external_source = externalSource?.name,
    external_id = externalId,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = deletedAt,
)

fun Expense.shareEntities() = shares.map {
    ExpenseShareEntity(
        id = shareId(id.value, it.personId.value),
        expense_id = id.value,
        person_id = it.personId.value,
        paid_minor_units = it.paidShare.minorUnits,
        owed_minor_units = it.owedShare.minorUnits,
    )
}

fun Comment.toEntity() = CommentEntity(
    id = id.value,
    expense_id = expenseId.value,
    author_id = authorId.value,
    content = content,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = deletedAt,
)

fun CommentEntity.toDomain() = Comment(
    id = CommentId(id),
    expenseId = ExpenseId(expense_id),
    authorId = PersonId(author_id),
    content = content,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)

fun UserPreferences.toEntity() = UserPreferencesEntity(
    id = 0,
    home_currency_code = homeCurrency.code,
    convert_to_home_in_ui = convertToHomeInUi,
    fx_source = fxSource.name,
    locale = locale,
    first_day_of_week = firstDayOfWeek,
    theme = theme.name,
    use_dynamic_color = useDynamicColor,
    decimal_separator = decimalSeparator?.toString(),
    biometric_lock = biometricLock,
    push_enabled = pushEnabled,
    onboarding_completed_at = onboardingCompletedAt,
)

fun UserPreferencesEntity.toDomain() = UserPreferences(
    homeCurrency = Currency.ofCodeOrDefault(home_currency_code),
    convertToHomeInUi = convert_to_home_in_ui,
    fxSource = FxSource.valueOf(fx_source),
    locale = locale,
    firstDayOfWeek = first_day_of_week,
    theme = ThemePreference.valueOf(theme),
    useDynamicColor = use_dynamic_color,
    decimalSeparator = decimal_separator?.firstOrNull(),
    biometricLock = biometric_lock,
    pushEnabled = push_enabled,
    onboardingCompletedAt = onboarding_completed_at,
)
