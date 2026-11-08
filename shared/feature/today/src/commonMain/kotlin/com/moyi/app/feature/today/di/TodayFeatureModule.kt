package com.moyi.app.feature.today.di

import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.RepositoryDailyLoop
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.reveal.RevealViewModel
import com.moyi.app.feature.today.streak.StreakViewModel
import com.moyi.app.feature.today.today.TodayViewModel
import com.moyi.app.feature.today.write.WriteViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The daily loop's ViewModels, over one shared [DailyLoop] backed by the domain's
 * BondRepository and TodayRepository (bound by the data layer, assembled in :shared).
 * The `-PmoyiUiPreview=today` build binds its preview loop over this one (KoinInit).
 */
val todayFeatureModule = module {
    single<DailyLoop> { RepositoryDailyLoop(get(), get()) }
    single { RevealLedger() }
    // Explicit lambdas, not viewModelOf: the key factory and the reveal's holds have defaults Koin should not resolve.
    viewModel { TodayViewModel(get(), get(), get()) }
    viewModel { (editing: Boolean) -> WriteViewModel(editing, get()) }
    viewModel { RevealViewModel(get()) }
    // The Archive: one feed shared by the list and the day view, over the domain's ArchiveRepository.
    single { ArchiveFeed(get(), get()) }
    viewModel { ArchiveViewModel(get()) }
    viewModel { (date: String) -> ArchiveDayViewModel(date, get(), get(), get(), get()) }
    // The Streak screen, from the Archive's "Your streak", over the domain's StreakRepository.
    viewModel { StreakViewModel(get(), get()) }
}
