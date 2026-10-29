package com.moyi.app

import com.moyi.app.data.network.networkModule
import com.moyi.app.data.repository.repositoryModules
import com.moyi.app.data.session.sessionModules
import com.moyi.app.feature.auth.di.authFeatureModule
import com.moyi.app.feature.onboarding.di.onboardingFeatureModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** The shell's own definitions. */
val appModule: Module = module {
    viewModelOf(::AppViewModel)
}

/** Every module the app runs with, data layer first. The platform ones need `androidContext` on Android. */
val appModules: List<Module> = listOf(networkModule) + sessionModules + repositoryModules + authFeatureModule + onboardingFeatureModule + appModule

/**
 * Starts Koin once per process. Android calls it from `MoyiApplication`
 * with `androidContext`; iOS from `MainViewController`.
 */
fun initKoin(config: KoinAppDeclaration = {}): KoinApplication = startKoin {
    config()
    modules(appModules)
}
