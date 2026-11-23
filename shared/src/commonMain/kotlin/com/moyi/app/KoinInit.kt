package com.moyi.app

import com.moyi.app.data.network.ApiConfig
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
import com.moyi.app.feature.support.di.supportFeatureModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** The shell's own definitions. */
val appModule: Module = module {
    viewModelOf(::AppViewModel)
    // One per process: the platform entries put a tapped push in it, the shell takes it out.
    single { PushTapInbox() }
    // The same for a link to getmoyi.app that opened the app (LinkRouting).
    single { LinkInbox() }
}

/**
 * Every module the app runs with, data layer first, talking to [apiBaseUrl]. The platform
 * ones need `androidContext` on Android.
 */
fun appModules(apiBaseUrl: String): List<Module> = listOf(networkModule(ApiConfig(apiBaseUrl))) + sessionModules + databaseModules + repositoryModules + authFeatureModule + onboardingFeatureModule + todayFeatureModule + bondFeatureModule + supportFeatureModule + appModule

/**
 * Starts Koin once per process, talking to [apiBaseUrl]. The app decides the URL per build
 * (CLAUDE.md, Configuration): Android's `MoyiApplication` passes `BuildConfig.API_BASE_URL`
 * with `androidContext`; iOS's entry points pass Info.plist's `MoyiApiBaseUrl`
 * (`startKoinOnce`). Debug builds point at a local backend, release builds at production.
 */
fun initKoin(apiBaseUrl: String, config: KoinAppDeclaration = {}): KoinApplication = startKoin {
    config()
    modules(appModules(apiBaseUrl))
    // DEBUG ONLY: the onboarding preview runs on fake bonds (UiPreviewIndex), the
    // today preview on a fake day (PreviewDailyLoop). The real app binds neither.
    if (UiPreviewConfig.UI_PREVIEW == "onboarding") modules(previewDataModule)
    if (UiPreviewConfig.UI_PREVIEW == "today") modules(previewTodayModule)
}
