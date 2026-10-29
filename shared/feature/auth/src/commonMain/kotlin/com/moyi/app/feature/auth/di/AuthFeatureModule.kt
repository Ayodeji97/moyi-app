package com.moyi.app.feature.auth.di

import com.moyi.app.feature.auth.common.DeviceLocale
import com.moyi.app.feature.auth.common.LegalLinks
import com.moyi.app.feature.auth.common.PlatformDeviceLocale
import com.moyi.app.feature.auth.home.HomePlaceholderViewModel
import com.moyi.app.feature.auth.login.LoginViewModel
import com.moyi.app.feature.auth.register.RegisterViewModel
import com.moyi.app.feature.auth.verify.VerifyViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The auth screens' ViewModels. AuthRepository comes from the data layer's module, assembled in :shared. */
val authFeatureModule = module {
    single<DeviceLocale> { PlatformDeviceLocale }
    single { LegalLinks.fromBuild() }
    viewModelOf(::RegisterViewModel)
    viewModelOf(::HomePlaceholderViewModel)
    // Navigation arguments arrive as Koin parameters (VerifyRoot / LoginRoot).
    viewModel { (email: String) -> VerifyViewModel(email, get()) }
    viewModel { (email: String, sessionEnded: Boolean) -> LoginViewModel(email, sessionEnded, get()) }
}
