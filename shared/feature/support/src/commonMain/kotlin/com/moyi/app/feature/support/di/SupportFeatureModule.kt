package com.moyi.app.feature.support.di

import com.moyi.app.feature.support.SupportViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Support's ViewModel, over the domain's SupportRepository (bound by the data layer, assembled in :shared). */
val supportFeatureModule = module {
    viewModel { SupportViewModel(repository = get()) }
}
