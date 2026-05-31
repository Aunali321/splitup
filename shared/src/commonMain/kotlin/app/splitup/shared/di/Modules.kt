package app.splitup.shared.di

import app.splitup.shared.data.local.DatabaseFactory
import app.splitup.shared.data.local.SplitUpDatabase
import app.splitup.shared.data.local.build
import app.splitup.shared.data.repository.RoomCommentRepository
import app.splitup.shared.data.repository.RoomExpenseRepository
import app.splitup.shared.data.repository.RoomGroupRepository
import app.splitup.shared.data.repository.RoomPersonRepository
import app.splitup.shared.data.repository.RoomSettlementRepository
import app.splitup.shared.data.repository.LocalDataReset
import app.splitup.shared.data.repository.RoomLocalDataReset
import app.splitup.shared.data.repository.RoomUserPreferencesRepository
import app.splitup.shared.data.repository.UserPreferencesRepository
import app.splitup.shared.domain.repository.CommentRepository
import app.splitup.shared.domain.repository.ExpenseRepository
import app.splitup.shared.domain.repository.GroupRepository
import app.splitup.shared.domain.repository.PersonRepository
import app.splitup.shared.domain.repository.SettlementRepository
import app.splitup.shared.domain.usecase.AddExpenseUseCase
import app.splitup.shared.domain.usecase.EditExpenseUseCase
import app.splitup.shared.domain.usecase.SettleUpUseCase
import app.splitup.shared.splitwise.SplitwiseImporter
import app.splitup.shared.splitwise.SplitwiseOAuth
import app.splitup.shared.util.IdGenerator
import app.splitup.shared.util.UuidIdGenerator
import app.splitup.shared.util.httpEngine
import kotlinx.datetime.Clock
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
    single { get<SplitUpDatabase>().settlementDao() }
    single { get<SplitUpDatabase>().commentDao() }
    single { get<SplitUpDatabase>().categoryDao() }
    single { get<SplitUpDatabase>().exchangeRateDao() }
    single { get<SplitUpDatabase>().userPreferencesDao() }
    single { get<SplitUpDatabase>().maintenanceDao() }
}

val repositoryModule: Module = module {
    single<PersonRepository> { RoomPersonRepository(get(), get()) }
    single<GroupRepository> { RoomGroupRepository(get(), get()) }
    single<ExpenseRepository> { RoomExpenseRepository(get(), get()) }
    single<SettlementRepository> { RoomSettlementRepository(get(), get()) }
    single<CommentRepository> { RoomCommentRepository(get(), get()) }
    single<UserPreferencesRepository> { RoomUserPreferencesRepository(get()) }
    single<LocalDataReset> { RoomLocalDataReset(get()) }
}

val useCaseModule: Module = module {
    factory { AddExpenseUseCase(get(), get(), { get<IdGenerator>().next() }) }
    factory { EditExpenseUseCase(get(), get()) }
    factory { SettleUpUseCase(get(), get(), idGenerator = { get<IdGenerator>().next() }) }
}

val importerModule: Module = module {
    single { SplitwiseOAuth(engineProvider = { httpEngine() }) }
    single {
        SplitwiseImporter(
            engineProvider = { httpEngine() },
            people = get(),
            groups = get(),
            expenses = get(),
            idGenerator = get(),
            clock = get(),
        )
    }
}

val sharedModules: List<Module> = listOf(
    coreModule, databaseModule, repositoryModule, useCaseModule, importerModule,
)
