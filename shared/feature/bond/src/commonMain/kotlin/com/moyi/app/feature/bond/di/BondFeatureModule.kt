package com.moyi.app.feature.bond.di

import com.moyi.app.feature.bond.settings.BondSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Bond settings' ViewModel, over the domain's BondSettingsRepository and AuthRepository
 * (bound by the data layer, assembled in :shared). The bond's id comes from the route.
 */
val bondFeatureModule = module {
    viewModel { (bondId: String) -> BondSettingsViewModel(bondId, repository = get(), auth = get()) }
}
