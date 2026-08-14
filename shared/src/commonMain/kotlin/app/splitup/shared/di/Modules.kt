package app.splitup.shared.di

import app.splitup.shared.data.local.DatabaseFactory
import app.splitup.shared.data.local.SplitUpDatabase
import app.splitup.shared.data.local.build
import app.splitup.shared.data.repository.RoomCommentRepository
import app.splitup.shared.data.repository.RoomExpenseRepository
import app.splitup.shared.data.repository.RoomGroupRepository
import app.splitup.shared.data.repository.RoomPersonRepository
import app.splitup.shared.data.repository.RoomTransactionRunner
import app.splitup.shared.data.repository.LocalDataReset
import app.splitup.shared.data.repository.RoomLocalDataReset
import app.splitup.shared.data.repository.RoomUserPreferencesRepository
import app.splitup.shared.data.repository.UserPreferencesRepository
import app.splitup.shared.domain.repository.CommentRepository
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.repository.TransactionRunner
import app.splitup.shared.domain.usecase.AddExpenseUseCase
import app.splitup.shared.domain.usecase.EditExpenseUseCase
import app.splitup.shared.domain.usecase.MaterializeRecurringExpensesUseCase
import app.splitup.shared.domain.usecase.SettleUpUseCase
import app.splitup.shared.data.sync.SyncApi
import app.splitup.shared.data.sync.SyncConfig
import app.splitup.shared.data.sync.SyncTriggers
import app.splitup.shared.data.sync.SyncService
import app.splitup.shared.splitwise.SplitwiseImporter
import app.splitup.shared.splitwise.SplitwiseOAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.splitup.shared.util.IdGenerator
import app.splitup.shared.util.UuidIdGenerator
import app.splitup.shared.util.httpEngine
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

val coreModule: Module = module {
    single<Clock> { Clock.System }
    single<IdGenerator> { UuidIdGenerator }
}

val databaseModule: Module = module {
    single { get<DatabaseFactory>().build() }
    single { get<SplitUpDatabase>().personDao() }
    single { get<SplitUpDatabase>().groupDao() }
    single { get<SplitUpDatabase>().expenseDao() }
    single { get<SplitUpDatabase>().commentDao() }
    single { get<SplitUpDatabase>().userPreferencesDao() }
    single { get<SplitUpDatabase>().maintenanceDao() }
}

val repositoryModule: Module = module {
    single<PersonRepository> { RoomPersonRepository(get(), get()) }
    single<GroupRepository> { RoomGroupRepository(get(), get()) }
    single<ExpenseRepository> { RoomExpenseRepository(get(), get()) }
    single<CommentRepository> { RoomCommentRepository(get(), get()) }
    single<UserPreferencesRepository> { RoomUserPreferencesRepository(get()) }
    single<TransactionRunner> { RoomTransactionRunner(get()) }
    single<LocalDataReset> { RoomLocalDataReset(get(), get(), get()) }
}

val useCaseModule: Module = module {
    factory { AddExpenseUseCase(expenses = get(), clock = get(), idGenerator = { get<IdGenerator>().next() }) }
    factory { EditExpenseUseCase(get(), get()) }
    factory { SettleUpUseCase(get(), get(), idGenerator = { get<IdGenerator>().next() }) }
    factory { MaterializeRecurringExpensesUseCase(expenses = get(), clock = get()) }
}

val importerModule: Module = module {
    single { SplitwiseOAuth(engineProvider = { httpEngine() }) }
    single {
        SplitwiseImporter(
            engineProvider = { httpEngine() },
            transactions = get(),
            people = get(),
            groups = get(),
            expenses = get(),
            comments = get(),
            idGenerator = get(),
            clock = get(),
        )
    }
}

/**
 * Sync is optional: [SecretStore] and the server URL come from the platform module,
 * and nothing here runs until the user signs in.
 */
val syncModule: Module = module {
    single { SyncConfig(get()) }
    single { SyncApi(httpEngine()) { get<SyncConfig>().requireServerUrl() } }
    single { SyncTriggers(get()) }
    single {
        SyncService(
            room = get(),
            api = get(),
            secrets = get(),
            people = get(),
            config = get(),
            triggers = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}

val sharedModules: List<Module> = listOf(
    coreModule, databaseModule, repositoryModule, useCaseModule, importerModule, syncModule,
)
