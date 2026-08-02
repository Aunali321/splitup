package app.splitup.ui.di

import app.splitup.ui.RootViewModel
import app.splitup.ui.screens.account.AccountViewModel
import app.splitup.ui.screens.activity.ActivityViewModel
import app.splitup.ui.screens.addexpense.AddExpenseViewModel
import app.splitup.ui.screens.addexpense.ExpenseWithViewModel
import app.splitup.ui.screens.expense.ExpenseDetailViewModel
import app.splitup.ui.screens.friends.FriendDetailViewModel
import app.splitup.ui.screens.friends.FriendsViewModel
import app.splitup.ui.screens.groups.GroupBalancesViewModel
import app.splitup.ui.screens.groups.GroupDetailViewModel
import app.splitup.ui.screens.groups.GroupTotalsViewModel
import app.splitup.ui.screens.groups.GroupWhiteboardViewModel
import app.splitup.ui.screens.groups.GroupsViewModel
import app.splitup.ui.screens.groups.NonGroupViewModel
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.PersonId
import app.splitup.ui.oauth.OAuthCallbackBus
import app.splitup.ui.screens.importer.SplitwiseImportViewModel
import app.splitup.ui.screens.onboarding.OnboardingProfileViewModel
import app.splitup.ui.screens.onboarding.OnboardingViewModel
import app.splitup.ui.screens.account.EditProfileViewModel
import app.splitup.ui.screens.groups.GroupSettingsViewModel
import app.splitup.ui.screens.settings.SettingsViewModel
import app.splitup.ui.screens.sync.SyncViewModel
import app.splitup.ui.screens.settleup.SettleUpViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    single { OAuthCallbackBus() }

    viewModel { RootViewModel(get(), get(), get(), get()) }
    viewModel { OnboardingViewModel(get()) }
    viewModel { OnboardingProfileViewModel(get(), get(), get()) }
    viewModel { GroupsViewModel(get(), get(), get(), get()) }
    viewModel { params -> GroupDetailViewModel(params.get<GroupId>(), get(), get(), get()) }
    viewModel { params -> GroupBalancesViewModel(params.get<GroupId>(), get(), get(), get()) }
    viewModel { params -> GroupTotalsViewModel(params.get<GroupId>(), get(), get(), get()) }
    viewModel { NonGroupViewModel(get(), get()) }
    viewModel { params -> GroupWhiteboardViewModel(params.get<GroupId>(), get()) }
    viewModel { FriendsViewModel(get(), get(), get()) }
    viewModel { params -> FriendDetailViewModel(params.get<PersonId>(), get(), get()) }
    viewModel { ActivityViewModel(get(), get(), get()) }
    viewModel { AccountViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { EditProfileViewModel(get()) }
    viewModel { SyncViewModel(get(), get()) }
    viewModel { params -> GroupSettingsViewModel(params.get<GroupId>(), get(), get(), get(), get(), get()) }
    viewModel { params -> ExpenseDetailViewModel(params.get<ExpenseId>(), get(), get(), get(), get(), get(), get()) }
    // Scoped by the caller to the AddExpenseFlow nav-graph entry, so the draft is
    // shared with the pickers and dies when the flow pops.
    viewModel { params ->
        AddExpenseViewModel(
            groupId = params.getOrNull<GroupId>(),
            friendIds = params.get<List<PersonId>>(),
            expenseId = params.getOrNull<String>(),
            addExpense = get(),
            editExpense = get(),
            prefs = get(),
            people = get(),
            groups = get(),
            expenses = get(),
            imagePicker = get(),
        )
    }
    viewModel { ExpenseWithViewModel(get(), get()) }
    viewModel { SplitwiseImportViewModel(get(), get(), get(), get()) }
    viewModel { params ->
        SettleUpViewModel(
            params.getOrNull<GroupId>(),
            params.getOrNull<PersonId>(),
            get(), get(), get(), get(),
        )
    }
}
