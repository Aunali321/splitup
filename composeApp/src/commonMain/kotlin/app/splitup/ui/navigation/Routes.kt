package app.splitup.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations. The router renders one of these at a time.
 * Splitwise-style bottom-tab structure with Friends, Groups, Activity, Account.
 */
sealed interface Route {

    // Onboarding (shown only on first run)
    @Serializable data object OnboardingWelcome : Route
    @Serializable data object OnboardingCurrency : Route
    @Serializable data object OnboardingProfile : Route
    @Serializable data object OnboardingImport : Route

    // Main tabs
    @Serializable data object Friends : Route
    @Serializable data object Groups : Route
    @Serializable data object Activity : Route
    @Serializable data object Account : Route

    // Detail screens
    @Serializable data class GroupDetail(val groupId: String) : Route
    @Serializable data class FriendDetail(val friendId: String) : Route
    @Serializable data class ExpenseDetail(val expenseId: String) : Route

    // Modal flows
    @Serializable data class AddExpense(val groupId: String? = null, val friendId: String? = null) : Route
    @Serializable data class SettleUp(val groupId: String? = null, val friendId: String? = null) : Route
    @Serializable data object SplitwiseImport : Route
    @Serializable data object Settings : Route
}

enum class BottomTab(val route: Route, val label: String) {
    FRIENDS(Route.Friends, "Friends"),
    GROUPS(Route.Groups, "Groups"),
    ACTIVITY(Route.Activity, "Activity"),
    ACCOUNT(Route.Account, "Account"),
}
