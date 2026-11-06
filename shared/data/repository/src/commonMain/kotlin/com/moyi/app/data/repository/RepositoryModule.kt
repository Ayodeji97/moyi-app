package com.moyi.app.data.repository

import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.today.TodayRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get()) }
    single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    single<BondRepository> { BondRepositoryImpl(get(), get()) }
    single<BondSettingsRepository> { BondSettingsRepositoryImpl(get(), get()) }
    single<TodayRepository> { TodayRepositoryImpl(get(), get()) }
}

/** The platform's [DeviceInfoProvider]; Android needs `androidContext` for the app version. */
expect val platformRepositoryModule: Module

val repositoryModules: List<Module> = listOf(repositoryModule, platformRepositoryModule)
