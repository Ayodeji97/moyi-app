package com.moyi.app.data.repository

import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.data.session.PersonalData
import com.moyi.app.data.session.SessionManager
import com.moyi.app.domain.archive.ArchiveRepository
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.streak.StreakRepository
import com.moyi.app.domain.today.TodayCache
import com.moyi.app.domain.today.TodayRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get()) }
    single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    single<BondRepository> { BondRepositoryImpl(get(), get()) }
    single<BondSettingsRepository> { BondSettingsRepositoryImpl(get(), get()) }
    single<TodayRepository> { TodayRepositoryImpl(get(), get()) }
    single<ArchiveRepository> { ArchiveRepositoryImpl(get(), get()) }
    single<StreakRepository> { StreakRepositoryImpl(get(), get()) }
    // Today's cache-then-network (doc 13 §5), keyed by the signed-in person.
    single<TodayCache> { SqlTodayCache(get(), get<SessionManager>().sessionState) }
    // What the session wipes when one ends or starts: the whole local database.
    single<PersonalData> {
        val database = get<LocalDatabase>()
        PersonalData { database.wipe() }
    }
}

/** The platform's [DeviceInfoProvider]; Android needs `androidContext` for the app version. */
expect val platformRepositoryModule: Module

val repositoryModules: List<Module> = listOf(repositoryModule, platformRepositoryModule)
