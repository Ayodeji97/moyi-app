package com.moyi.app.data.repository

import com.moyi.app.domain.auth.AuthRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get()) }
}

/** The platform's [DeviceInfoProvider]; Android needs `androidContext` for the app version. */
expect val platformRepositoryModule: Module

val repositoryModules: List<Module> = listOf(repositoryModule, platformRepositoryModule)
