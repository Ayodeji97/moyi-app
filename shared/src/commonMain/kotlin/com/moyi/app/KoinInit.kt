package com.moyi.app

import com.moyi.app.data.network.networkModule
import com.moyi.app.debug.previewDataModule
import com.moyi.app.debug.previewTodayModule
import com.moyi.app.data.database.databaseModules
import com.moyi.app.data.repository.repositoryModules
import com.moyi.app.data.session.sessionModules
import com.moyi.app.feature.auth.di.authFeatureModule
import com.moyi.app.feature.onboarding.di.onboardingFeatureModule
import com.moyi.app.feature.bond.di.bondFeatureModule
import com.moyi.app.feature.today.di.todayFeatureModule
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
val appModules: List<Module> = listOf(networkModule) + sessionModules + databaseModules + repositoryModules + authFeatureModule + onboardingFeatureModule + todayFeatureModule + bondFeatureModule + appModule

/**
 * Starts Koin once per process. Android calls it from `MoyiApplication`
 * with `androidContext`; iOS from `MainViewController`.
 */
fun initKoin(config: KoinAppDeclaration = {}): KoinApplication = startKoin {
    config()
    modules(appModules)
    // DEBUG ONLY: the onboarding preview runs on fake bonds (UiPreviewIndex), the
    // today preview on a fake day (PreviewDailyLoop). The real app binds neither.
    if (UiPreviewConfig.UI_PREVIEW == "onboarding") modules(previewDataModule)
    if (UiPreviewConfig.UI_PREVIEW == "today") modules(previewTodayModule)
}
