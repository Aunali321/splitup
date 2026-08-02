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
    @Serializable data class GroupBalances(val groupId: String) : Route
    @Serializable data object NonGroupExpenses : Route
    @Serializable data class GroupTotals(val groupId: String) : Route
    @Serializable data class GroupWhiteboard(val groupId: String) : Route
    @Serializable data class FriendDetail(val friendId: String) : Route
    @Serializable data class ExpenseDetail(val expenseId: String) : Route

    // Modal flows. AddExpenseFlow is a nested graph whose entry scopes the
    // shared draft ViewModel — popping the flow clears the draft.
    @Serializable data class AddExpenseFlow(
        val groupId: String? = null,
        val friendIds: List<String> = emptyList(),
        val expenseId: String? = null,
    ) : Route
    @Serializable data object AddExpenseForm : Route
    @Serializable data object PaidByPicker : Route
    @Serializable data object SplitPicker : Route
    /** "With you and:" chooser for expenses started without a group/friend context. */
    @Serializable data object ExpenseWith : Route
    @Serializable data class SettleUp(val groupId: String? = null, val friendId: String? = null) : Route
    @Serializable data object SplitwiseImport : Route
    @Serializable data object Settings : Route
    @Serializable data object Sync : Route
    @Serializable data object EditProfile : Route
    @Serializable data class GroupSettings(val groupId: String) : Route
}

enum class BottomTab(val route: Route, val label: String) {
    GROUPS(Route.Groups, "Groups"),
    FRIENDS(Route.Friends, "Friends"),
    ACTIVITY(Route.Activity, "Activity"),
    ACCOUNT(Route.Account, "Account"),
}
