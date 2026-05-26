package app.splitup.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.splitup.ui.navigation.BottomTab
import app.splitup.ui.navigation.Motion
import app.splitup.ui.navigation.Route
import app.splitup.ui.screens.account.AccountScreen
import app.splitup.ui.screens.activity.ActivityScreen
import app.splitup.ui.screens.addexpense.AddExpenseScreen
import app.splitup.ui.screens.expense.ExpenseDetailScreen
import app.splitup.ui.screens.friends.FriendDetailScreen
import app.splitup.ui.screens.friends.FriendsScreen
import app.splitup.ui.screens.groups.GroupDetailScreen
import app.splitup.ui.screens.groups.GroupsScreen
import app.splitup.ui.screens.importer.SplitwiseImportScreen
import app.splitup.ui.screens.onboarding.OnboardingCurrencyScreen
import app.splitup.ui.screens.onboarding.OnboardingImportScreen
import app.splitup.ui.screens.onboarding.OnboardingProfileScreen
import app.splitup.ui.screens.onboarding.OnboardingWelcomeScreen
import app.splitup.ui.screens.settings.SettingsScreen
import app.splitup.ui.screens.settleup.SettleUpScreen
import app.splitup.ui.theme.SplitUpTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    val rootViewModel: RootViewModel = koinViewModel()
    val prefs by rootViewModel.preferences.collectAsStateWithLifecycle()

    val darkTheme = when (prefs.theme) {
        app.splitup.shared.domain.model.ThemePreference.DARK -> true
        app.splitup.shared.domain.model.ThemePreference.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    SplitUpTheme(darkTheme = darkTheme, dynamicColor = prefs.useDynamicColor) {
        Surface(color = MaterialTheme.colorScheme.background) {
            if (prefs.needsOnboarding) OnboardingNav() else MainNav()
        }
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
    val currentTab = BottomTab.entries.firstOrNull { tab ->
        backStack?.destination?.route?.contains(tab.route::class.qualifiedName.orEmpty()) == true
    } ?: BottomTab.GROUPS

    Scaffold(
        bottomBar = {
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
                ) { FriendsScreen(onOpenFriend = { nav.navigate(Route.FriendDetail(it.value)) }) }

                composable<Route.Groups>(
                    enterTransition = Motion.tabFade,
                    exitTransition = Motion.tabFadeExit,
                    popEnterTransition = Motion.tabFade,
                    popExitTransition = Motion.tabFadeExit,
                ) { GroupsScreen(onOpenGroup = { nav.navigate(Route.GroupDetail(it.value)) }) }

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
                    )
                }

                composable<Route.GroupDetail> { entry ->
                    val args = entry.toRoute<Route.GroupDetail>()
                    GroupDetailScreen(
                        groupId = args.groupId,
                        onBack = { nav.popBackStack() },
                        onAddExpense = { nav.navigate(Route.AddExpense(groupId = args.groupId)) },
                        onSettleUp = { nav.navigate(Route.SettleUp(groupId = args.groupId)) },
                        onOpenExpense = { nav.navigate(Route.ExpenseDetail(it.value)) },
                    )
                }
                composable<Route.FriendDetail> { entry ->
                    val args = entry.toRoute<Route.FriendDetail>()
                    FriendDetailScreen(
                        friendId = args.friendId,
                        onBack = { nav.popBackStack() },
                        onAddExpense = { nav.navigate(Route.AddExpense(friendId = args.friendId)) },
                        onSettleUp = { nav.navigate(Route.SettleUp(friendId = args.friendId)) },
                        onOpenExpense = { nav.navigate(Route.ExpenseDetail(it.value)) },
                    )
                }
                composable<Route.ExpenseDetail> { entry ->
                    val args = entry.toRoute<Route.ExpenseDetail>()
                    ExpenseDetailScreen(expenseId = args.expenseId, onBack = { nav.popBackStack() })
                }

                composable<Route.AddExpense>(
                    enterTransition = Motion.modalEnter,
                    exitTransition = Motion.modalExit,
                    popEnterTransition = Motion.modalPopEnter,
                    popExitTransition = Motion.modalPopExit,
                ) { entry ->
                    val args = entry.toRoute<Route.AddExpense>()
                    AddExpenseScreen(
                        groupId = args.groupId,
                        friendId = args.friendId,
                        onDone = { nav.popBackStack() },
                    )
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
            }
        }
    }
}

private fun tabIcon(tab: BottomTab, selected: Boolean): ImageVector = when (tab) {
    BottomTab.FRIENDS -> if (selected) Icons.Rounded.Person else Icons.Outlined.PersonOutline
    BottomTab.GROUPS -> if (selected) Icons.Rounded.Groups else Icons.Outlined.Groups
    BottomTab.ACTIVITY -> if (selected) Icons.Rounded.Notifications else Icons.Outlined.Notifications
    BottomTab.ACCOUNT -> if (selected) Icons.Rounded.AccountCircle else Icons.Outlined.AccountCircle
}

@Composable
expect fun isSystemInDarkTheme(): Boolean
