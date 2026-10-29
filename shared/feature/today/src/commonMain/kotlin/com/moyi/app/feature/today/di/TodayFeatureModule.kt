package com.moyi.app.feature.today.di

import com.moyi.app.feature.today.common.FakeDailyLoop
import com.moyi.app.feature.today.reveal.RevealViewModel
import com.moyi.app.feature.today.today.TodayViewModel
import com.moyi.app.feature.today.write.WriteViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The daily loop's ViewModels, over one shared FAKE day.
 *
 * TODO(logic): FakeDailyLoop gives way to a TodayRepository from the data layer,
 * assembled in :shared like AuthRepository.
 */
val todayFeatureModule = module {
    single { FakeDailyLoop() }
    // Explicit lambdas, not viewModelOf: the fake latencies and holds have defaults Koin should not resolve.
    viewModel { TodayViewModel(get()) }
    viewModel { (editing: Boolean) -> WriteViewModel(editing, get()) }
    viewModel { RevealViewModel(get()) }
}
