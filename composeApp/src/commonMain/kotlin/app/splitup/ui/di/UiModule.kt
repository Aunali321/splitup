package app.splitup.ui.di

import app.splitup.ui.RootViewModel
import app.splitup.ui.screens.account.AccountViewModel
import app.splitup.ui.screens.activity.ActivityViewModel
import app.splitup.ui.screens.addexpense.AddExpenseViewModel
import app.splitup.ui.screens.expense.ExpenseDetailViewModel
import app.splitup.ui.screens.friends.FriendDetailViewModel
import app.splitup.ui.screens.friends.FriendsViewModel
import app.splitup.ui.screens.groups.GroupDetailViewModel
import app.splitup.ui.screens.groups.GroupsViewModel
import app.splitup.shared.domain.model.ExpenseId
import app.splitup.shared.domain.model.GroupId
import app.splitup.shared.domain.model.PersonId
import app.splitup.ui.oauth.OAuthCallbackBus
import app.splitup.ui.screens.importer.SplitwiseImportViewModel
import app.splitup.ui.screens.onboarding.OnboardingProfileViewModel
import app.splitup.ui.screens.onboarding.OnboardingViewModel
import app.splitup.ui.screens.settleup.SettleUpViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    single { OAuthCallbackBus() }

    viewModel { RootViewModel(get()) }
    viewModel { OnboardingViewModel(get()) }
    viewModel { OnboardingProfileViewModel(get(), get(), get()) }
    viewModel { GroupsViewModel(get(), get(), get()) }
    viewModel { params -> GroupDetailViewModel(params.get<GroupId>(), get(), get(), get()) }
    viewModel { FriendsViewModel(get(), get()) }
    viewModel { params -> FriendDetailViewModel(params.get<PersonId>(), get(), get()) }
    viewModel { ActivityViewModel(get(), get()) }
    viewModel { AccountViewModel(get()) }
    viewModel { params -> ExpenseDetailViewModel(params.get<ExpenseId>(), get(), get()) }
    // Single — shared across the AddExpense / PaidByPicker / SplitPicker screens
    // so the form draft survives navigating into a picker and back.
    single { AddExpenseViewModel(get(), get(), get(), get()) }
    viewModel { SplitwiseImportViewModel(get(), get(), get(), get()) }
    viewModel { params ->
        SettleUpViewModel(
            params.getOrNull<GroupId>(),
            params.getOrNull<PersonId>(),
            get(), get(), get(),
        )
    }
}
