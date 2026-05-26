package app.splitup.shared.splitwise

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Minimal DTOs matching dev.splitwise.com responses. Only the fields we actually
// import; ignoreUnknownKeys lets the rest pass through.

@Serializable
data class SwCurrentUserResponse(val user: SwUser)

@Serializable
data class SwUser(
    val id: Long,
    val first_name: String?,
    val last_name: String? = null,
    val email: String? = null,
    val registration_status: String? = null,
    val picture: SwPicture? = null,
    val default_currency: String? = null,
    val country_code: String? = null,
    val locale: String? = null,
)

@Serializable
data class SwPicture(val medium: String? = null, val large: String? = null)

@Serializable
data class SwGroupsResponse(val groups: List<SwGroup>)

@Serializable
data class SwGroup(
    val id: Long,
    val name: String,
    val group_type: String? = null,
    val updated_at: String? = null,
    val created_at: String? = null,
    val members: List<SwMember> = emptyList(),
    val simplify_by_default: Boolean = true,
    val whiteboard: String? = null,
    val avatar: SwPicture? = null,
    val cover_photo: SwPicture? = null,
    val original_debts: List<SwDebt> = emptyList(),
    val simplified_debts: List<SwDebt> = emptyList(),
)

@Serializable
data class SwMember(
    val id: Long,
    val first_name: String?,
    val last_name: String? = null,
    val email: String? = null,
    val registration_status: String? = null,
    val picture: SwPicture? = null,
    val balance: List<SwBalanceItem> = emptyList(),
)

@Serializable
data class SwBalanceItem(val currency_code: String, val amount: String)

@Serializable
data class SwDebt(val from: Long, val to: Long, val amount: String, val currency_code: String)

@Serializable
data class SwFriendsResponse(val friends: List<SwFriend>)

@Serializable
data class SwFriend(
    val id: Long,
    val first_name: String?,
    val last_name: String? = null,
    val email: String? = null,
    val registration_status: String? = null,
    val picture: SwPicture? = null,
    val balance: List<SwBalanceItem> = emptyList(),
    val groups: List<SwFriendGroup> = emptyList(),
    val updated_at: String? = null,
)

@Serializable
data class SwFriendGroup(val group_id: Long, val balance: List<SwBalanceItem> = emptyList())

@Serializable
data class SwExpensesResponse(val expenses: List<SwExpense>)

@Serializable
data class SwExpense(
    val id: Long,
    val group_id: Long? = null,
    val description: String,
    val details: String? = null,
    val date: String,
    val cost: String,
    val currency_code: String,
    val payment: Boolean = false,
    val transaction_method: String? = null,
    val deleted_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val created_by: SwUser? = null,
    val category: SwCategory? = null,
    val repeat_interval: String? = null,
    val users: List<SwExpenseUser> = emptyList(),
    val receipt: SwReceipt? = null,
    val comments_count: Int = 0,
    val expense_bundle_id: Long? = null,
)

@Serializable
data class SwCategory(val id: Long, val name: String)

@Serializable
data class SwExpenseUser(
    val user: SwUser,
    val user_id: Long,
    val paid_share: String,
    val owed_share: String,
)

@Serializable
data class SwReceipt(val original: String? = null, val large: String? = null)

@Serializable
data class SwCategoriesResponse(val categories: List<SwTopCategory>)

@Serializable
data class SwTopCategory(val id: Long, val name: String, val icon: String? = null, val subcategories: List<SwSubcategory> = emptyList())

@Serializable
data class SwSubcategory(val id: Long, val name: String, val icon: String? = null)

@Serializable
data class SwCurrenciesResponse(val currencies: List<SwCurrency>)

@Serializable
data class SwCurrency(val currency_code: String, val unit: String)

@Serializable
data class SwCommentsResponse(val comments: List<SwComment>)

@Serializable
data class SwComment(
    val id: Long,
    val content: String,
    @SerialName("comment_type") val commentType: String? = null,
    val relation_id: Long? = null,
    val relation_type: String? = null,
    val created_at: String? = null,
    val deleted_at: String? = null,
    val user: SwUser,
)
