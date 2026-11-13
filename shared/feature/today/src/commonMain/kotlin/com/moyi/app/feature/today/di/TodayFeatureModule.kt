package com.moyi.app.feature.today.di

import com.moyi.app.domain.media.MediaUploader
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.RepositoryDailyLoop
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.reveal.RevealViewModel
import com.moyi.app.feature.today.streak.StreakViewModel
import com.moyi.app.feature.today.today.TodayViewModel
import com.moyi.app.feature.today.write.WriteViewModel
import com.moyi.app.feature.today.write.randomIdempotencyKey
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The daily loop's ViewModels, over one shared [DailyLoop] backed by the domain's
 * BondRepository, TodayRepository, TodayCache and the outbox, and Write over the DraftStore (bound by the data layer, assembled
 * in :shared).
 * The `-PmoyiUiPreview=today` build binds its preview loop over this one (KoinInit).
 */
val todayFeatureModule = module {
    single<DailyLoop> { RepositoryDailyLoop(get(), get(), get(), get(), get(), archive = get(), emptyBonds = get()) }
    // Keyed by the signed-in person too: two members of one bond can share a phone.
    single { RevealLedger { (get<AuthRepository>().session.value as? SessionState.SignedIn)?.user?.id } }
    // Explicit lambdas, not viewModelOf: the key factory and the reveal's holds have defaults Koin should not resolve.
    viewModel { TodayViewModel(get(), get(), get(), get(), askMemory = get()) }
    viewModel { (editing: Boolean) ->
        WriteViewModel(editing, get(), get(), uploader = MediaUploader(get(), newIdempotencyKey = ::randomIdempotencyKey))
    }
    viewModel { RevealViewModel(get()) }
    // The Archive: one feed shared by the list and the day view, over the domain's ArchiveRepository and ArchiveCache.
    single { ArchiveFeed(get(), get(), get()) }
    viewModel { ArchiveViewModel(get()) }
    viewModel { (date: String) -> ArchiveDayViewModel(date, get(), get(), get(), get()) }
    // The Streak screen, from the Archive's "Your streak", over the domain's StreakRepository.
    viewModel { StreakViewModel(get(), get()) }
}
