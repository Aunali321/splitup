package app.splitup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.WindowInsets
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.PersonId
import app.splitup.ui.navigation.BottomTab
import app.splitup.ui.navigation.Motion
import app.splitup.ui.navigation.Route
import app.splitup.ui.screens.account.AccountScreen
import app.splitup.ui.screens.account.EditProfileScreen
import app.splitup.ui.screens.activity.ActivityScreen
import app.splitup.ui.screens.addexpense.AddExpenseScreen
import app.splitup.ui.screens.addexpense.AddExpenseViewModel
import app.splitup.ui.screens.addexpense.ExpenseWithScreen
import app.splitup.ui.screens.addexpense.PaidByPickerScreen
import app.splitup.ui.screens.addexpense.SplitPickerScreen
import app.splitup.ui.screens.expense.ExpenseDetailScreen
import app.splitup.ui.screens.friends.FriendDetailScreen
import app.splitup.ui.screens.friends.FriendsScreen
import app.splitup.ui.screens.groups.GroupBalancesScreen
import app.splitup.ui.screens.groups.GroupDetailScreen
import app.splitup.ui.screens.groups.GroupSettingsScreen
import app.splitup.ui.screens.groups.GroupTotalsScreen
import app.splitup.ui.screens.groups.GroupWhiteboardScreen
import app.splitup.ui.screens.groups.GroupsScreen
import app.splitup.ui.screens.groups.NonGroupScreen
import app.splitup.ui.screens.importer.SplitwiseImportScreen
import app.splitup.ui.screens.onboarding.OnboardingCurrencyScreen
import app.splitup.ui.screens.onboarding.OnboardingImportScreen
import app.splitup.ui.screens.onboarding.OnboardingProfileScreen
import app.splitup.ui.screens.onboarding.OnboardingWelcomeScreen
import app.splitup.ui.screens.settings.SettingsScreen
import app.splitup.ui.screens.sync.SyncScreen
import app.splitup.ui.screens.settleup.SettleUpScreen
import app.splitup.ui.theme.SplitUpTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun App() {
    val rootViewModel: RootViewModel = koinViewModel()
    val prefs by rootViewModel.preferences.collectAsStateWithLifecycle()
    val locked by rootViewModel.locked.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { rootViewModel.relock() }

    val darkTheme = when (prefs?.theme) {
        app.splitup.shared.domain.model.ThemePreference.DARK -> true
        app.splitup.shared.domain.model.ThemePreference.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    SplitUpTheme(darkTheme = darkTheme, dynamicColor = prefs?.useDynamicColor ?: true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            when {
                prefs == null || locked == null -> {}
                locked == true -> LockScreen(onUnlock = rootViewModel::unlock)
                prefs?.needsOnboarding == true -> OnboardingNav()
                else -> MainNav()
            }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("SplitUp is locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onUnlock) { Text("Unlock") }
    }
}

@Composable
private fun OnboardingNav() {
    val nav = rememberNavController()
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        NavHost(
            navController = nav,
            startDestination = Route.OnboardingWelcome,
            enterTransition = Motion.onboardingEnter,
            exitTransition = Motion.onboardingExit,
            popEnterTransition = Motion.onboardingPopEnter,
            popExitTransition = Motion.onboardingPopExit,
        ) {
            composable<Route.OnboardingWelcome> { OnboardingWelcomeScreen(onContinue = { nav.navigate(Route.OnboardingCurrency) }) }
            composable<Route.OnboardingCurrency> { OnboardingCurrencyScreen(onContinue = { nav.navigate(Route.OnboardingProfile) }) }
            composable<Route.OnboardingProfile> { OnboardingProfileScreen(onContinue = { nav.navigate(Route.OnboardingImport) }) }
            composable<Route.OnboardingImport> { OnboardingImportScreen() }
        }
    }
}

@Composable
private fun MainNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    // Null on non-tab destinations — the bottom bar only exists on tab roots.
    val currentTab = BottomTab.entries.firstOrNull { tab ->
        backStack?.destination?.hasRoute(tab.route::class) == true
    }

    Scaffold(
        // The inner screens use their own Scaffold + TopAppBar, which already
        // handle the status-bar inset. The bottom NavigationBar is the only
        // thing we hand back to inner screens via PaddingValues.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    BottomTab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tabIcon(tab, selected), contentDescription = tab.label) },
                            label = {
                                Text(
                                    tab.label,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { inner: PaddingValues ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            // Default motion is "forward" — most navigations push a detail screen
            // on top of a list. Modal destinations override to slide-from-bottom;
            // bottom-tab roots override to a quick crossfade.
            NavHost(
                navController = nav,
                startDestination = Route.Groups,
                enterTransition = Motion.forwardEnter,
                exitTransition = Motion.forwardExit,
                popEnterTransition = Motion.backEnter,
                popExitTransition = Motion.backExit,
            ) {
                composable<Route.Friends>(
                    enterTransition = Motion.tabFade,
                    exitTransition = Motion.tabFadeExit,
                    popEnterTransition = Motion.tabFade,
                    popExitTransition = Motion.tabFadeExit,
                ) {
                    FriendsScreen(
                        onOpenFriend = { nav.navigate(Route.FriendDetail(it.value)) },
                        onAddExpense = { nav.navigate(Route.ExpenseWith) },
                    )
                }

                composable<Route.Groups>(
                    enterTransition = Motion.tabFade,
                    exitTransition = Motion.tabFadeExit,
                    popEnterTransition = Motion.tabFade,
                    popExitTransition = Motion.tabFadeExit,
                ) {
                    GroupsScreen(
                        onOpenGroup = { nav.navigate(Route.GroupDetail(it.value)) },
                        onOpenNonGroup = { nav.navigate(Route.NonGroupExpenses) },
                        onAddExpense = { nav.navigate(Route.ExpenseWith) },
                    )
                }

                composable<Route.Activity>(
                    enterTransition = Motion.tabFade,
                    exitTransition = Motion.tabFadeExit,
                    popEnterTransition = Motion.tabFade,
                    popExitTransition = Motion.tabFadeExit,
                ) { ActivityScreen(onOpenExpense = { nav.navigate(Route.ExpenseDetail(it.value)) }) }

                composable<Route.Account>(
                    enterTransition = Motion.tabFade,
                    exitTransition = Motion.tabFadeExit,
                    popEnterTransition = Motion.tabFade,
                    popExitTransition = Motion.tabFadeExit,
                ) {
                    AccountScreen(
                        onOpenSettings = { nav.navigate(Route.Settings) },
                        onOpenImport = { nav.navigate(Route.SplitwiseImport) },
                        onOpenEditProfile = { nav.navigate(Route.EditProfile) },
                        onOpenSync = { nav.navigate(Route.Sync) },
                    )
                }

                composable<Route.GroupDetail> { entry ->
                    val args = entry.toRoute<Route.GroupDetail>()
                    GroupDetailScreen(
                        groupId = args.groupId,
                        onBack = { nav.popBackStack() },
                        onAddExpense = { nav.navigate(Route.AddExpenseFlow(groupId = args.groupId)) },
                        onSettleUp = { nav.navigate(Route.SettleUp(groupId = args.groupId)) },
                        onOpenExpense = { nav.navigate(Route.ExpenseDetail(it.value)) },
                        onOpenSettings = { nav.navigate(Route.GroupSettings(args.groupId)) },
                        onOpenBalances = { nav.navigate(Route.GroupBalances(args.groupId)) },
                        onOpenTotals = { nav.navigate(Route.GroupTotals(args.groupId)) },
                        onOpenWhiteboard = { nav.navigate(Route.GroupWhiteboard(args.groupId)) },
                    )
                }
                composable<Route.GroupBalances> { entry ->
                    val args = entry.toRoute<Route.GroupBalances>()
                    GroupBalancesScreen(
                        groupId = args.groupId,
                        onBack = { nav.popBackStack() },
                        onSettleUp = { nav.navigate(Route.SettleUp(groupId = args.groupId)) },
                    )
                }
                composable<Route.NonGroupExpenses> {
                    NonGroupScreen(
                        onBack = { nav.popBackStack() },
                        onAddExpense = { nav.navigate(Route.ExpenseWith) },
                        onSettleUp = { nav.navigate(Route.SettleUp()) },
                        onOpenExpense = { nav.navigate(Route.ExpenseDetail(it.value)) },
                    )
                }
                composable<Route.GroupTotals> { entry ->
                    val args = entry.toRoute<Route.GroupTotals>()
                    GroupTotalsScreen(groupId = args.groupId, onBack = { nav.popBackStack() })
                }
                composable<Route.GroupWhiteboard> { entry ->
                    val args = entry.toRoute<Route.GroupWhiteboard>()
                    GroupWhiteboardScreen(groupId = args.groupId, onBack = { nav.popBackStack() })
                }
                composable<Route.FriendDetail> { entry ->
                    val args = entry.toRoute<Route.FriendDetail>()
                    FriendDetailScreen(
                        friendId = args.friendId,
                        onBack = { nav.popBackStack() },
                        onAddExpense = { nav.navigate(Route.AddExpenseFlow(friendIds = listOf(args.friendId))) },
                        onSettleUp = { nav.navigate(Route.SettleUp(friendId = args.friendId)) },
                        onOpenExpense = { nav.navigate(Route.ExpenseDetail(it.value)) },
                    )
                }
                composable<Route.ExpenseWith>(
                    enterTransition = Motion.modalEnter,
                    exitTransition = Motion.modalExit,
                    popEnterTransition = Motion.modalPopEnter,
                    popExitTransition = Motion.modalPopExit,
                ) {
                    ExpenseWithScreen(
                        onBack = { nav.popBackStack() },
                        onPickGroup = { groupId ->
                            nav.navigate(Route.AddExpenseFlow(groupId = groupId.value)) {
                                popUpTo<Route.ExpenseWith> { inclusive = true }
                            }
                        },
                        onPickFriends = { ids ->
                            nav.navigate(Route.AddExpenseFlow(friendIds = ids.map { it.value })) {
                                popUpTo<Route.ExpenseWith> { inclusive = true }
                            }
                        },
                    )
                }
                composable<Route.ExpenseDetail> { entry ->
                    val args = entry.toRoute<Route.ExpenseDetail>()
                    ExpenseDetailScreen(
                        expenseId = args.expenseId,
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate(Route.AddExpenseFlow(expenseId = args.expenseId)) },
                    )
                }

                navigation<Route.AddExpenseFlow>(startDestination = Route.AddExpenseForm) {
                    composable<Route.AddExpenseForm>(
                        enterTransition = Motion.modalEnter,
                        exitTransition = Motion.modalExit,
                        popEnterTransition = Motion.modalPopEnter,
                        popExitTransition = Motion.modalPopExit,
                    ) { entry ->
                        AddExpenseScreen(
                            vm = entry.addExpenseViewModel(nav),
                            onDone = { nav.popBackStack() },
                            onOpenPaidBy = { nav.navigate(Route.PaidByPicker) },
                            onOpenSplit = { nav.navigate(Route.SplitPicker) },
                        )
                    }
                    composable<Route.PaidByPicker> { entry ->
                        PaidByPickerScreen(
                            vm = entry.addExpenseViewModel(nav),
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable<Route.SplitPicker> { entry ->
                        SplitPickerScreen(
                            vm = entry.addExpenseViewModel(nav),
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable<Route.SettleUp>(
                    enterTransition = Motion.modalEnter,
                    exitTransition = Motion.modalExit,
                    popEnterTransition = Motion.modalPopEnter,
                    popExitTransition = Motion.modalPopExit,
                ) { entry ->
                    val args = entry.toRoute<Route.SettleUp>()
                    SettleUpScreen(
                        groupId = args.groupId,
                        friendId = args.friendId,
                        onDone = { nav.popBackStack() },
                    )
                }
                composable<Route.SplitwiseImport>(
                    enterTransition = Motion.modalEnter,
                    exitTransition = Motion.modalExit,
                    popEnterTransition = Motion.modalPopEnter,
                    popExitTransition = Motion.modalPopExit,
                ) { SplitwiseImportScreen(onDone = { nav.popBackStack() }) }

                composable<Route.Settings> { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable<Route.Sync> { SyncScreen(onBack = { nav.popBackStack() }) }

                composable<Route.EditProfile> { EditProfileScreen(onBack = { nav.popBackStack() }) }

                composable<Route.GroupSettings> { entry ->
                    val args = entry.toRoute<Route.GroupSettings>()
                    GroupSettingsScreen(
                        groupId = args.groupId,
                        onBack = { nav.popBackStack() },
                        // After deleting, the GroupDetail behind us points at a now-missing
                        // group; pop straight back to the Groups list instead.
                        onDeleted = { nav.popBackStack<Route.Groups>(inclusive = false) },
                    )
                }
            }
        }
    }
}

/**
 * The AddExpense form and its pickers share one ViewModel scoped to the
 * AddExpenseFlow graph entry, which also carries the flow's arguments.
 */
@Composable
private fun NavBackStackEntry.addExpenseViewModel(nav: NavHostController): AddExpenseViewModel {
    val parent = remember(this) { nav.getBackStackEntry(destination.parent!!.id) }
    val args = parent.toRoute<Route.AddExpenseFlow>()
    return koinViewModel(
        viewModelStoreOwner = parent,
        parameters = {
            parametersOf(
                args.groupId?.let { GroupId(it) },
                args.friendIds.map { PersonId(it) },
                args.expenseId,
            )
        },
    )
}

private fun tabIcon(tab: BottomTab, selected: Boolean): ImageVector = when (tab) {
    BottomTab.FRIENDS -> if (selected) Icons.Rounded.Person else Icons.Outlined.PersonOutline
    BottomTab.GROUPS -> if (selected) Icons.Rounded.Groups else Icons.Outlined.Groups
    BottomTab.ACTIVITY -> if (selected) Icons.Rounded.Notifications else Icons.Outlined.Notifications
    BottomTab.ACCOUNT -> if (selected) Icons.Rounded.AccountCircle else Icons.Outlined.AccountCircle
}

@Composable
expect fun isSystemInDarkTheme(): Boolean
