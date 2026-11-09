package com.moyi.app.data.repository

import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.data.session.PersonalData
import com.moyi.app.data.session.SessionManager
import com.moyi.app.domain.archive.ArchiveCache
import com.moyi.app.domain.archive.ArchiveRepository
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.media.MediaRepository
import com.moyi.app.domain.notification.NotificationSettingsRepository
import com.moyi.app.domain.notification.PushTokenRegistration
import com.moyi.app.domain.notification.PushTokenSource
import com.moyi.app.domain.streak.StreakRepository
import com.moyi.app.domain.support.SupportRepository
import com.moyi.app.domain.today.DraftStore
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.domain.today.TodayCache
import com.moyi.app.domain.today.TodayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), pushTokens = get(), deviceInfo = get()) }
    single<NotificationSettingsRepository> { NotificationSettingsRepositoryImpl(get(), get()) }
    // No push provider until Firebase is set up: the one binding to swap for FCM (NoPushTokenSource).
    single<PushTokenSource> { NoPushTokenSource }
    single {
        PushTokenRegistrar(get(), get(), get(), get<SessionManager>().sessionState, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    } bind PushTokenRegistration::class
    single<AccountRepository> { AccountRepositoryImpl(get(), get()) }
    single<BondRepository> { BondRepositoryImpl(get(), get()) }
    single<BondSettingsRepository> { BondSettingsRepositoryImpl(get(), get()) }
    single<TodayRepository> { TodayRepositoryImpl(get(), get()) }
    // Photos (and voice notes, later): the media endpoints and the direct upload to storage.
    single<MediaRepository> { MediaRepositoryImpl(get(), get(), get()) }
    single<ArchiveRepository> { ArchiveRepositoryImpl(get(), get()) }
    single<StreakRepository> { StreakRepositoryImpl(get(), get()) }
    single<SupportRepository> { SupportRepositoryImpl(get(), get()) }
    // Today's cache-then-network (doc 13 §5), keyed by the signed-in person.
    single<TodayCache> { SqlTodayCache(get(), get<SessionManager>().sessionState) }
    single<ArchiveCache> { SqlArchiveCache(get(), get<SessionManager>().sessionState) }
    // The composer's drafts (doc 13 §5: local first, always), keyed by the signed-in person.
    single<DraftStore> { SqlDraftStore(get(), get<SessionManager>().sessionState) }
    // New entries sent while offline, and the one sender that retries them (doc 13 §5). The
    // scheduler and the connectivity watcher are the platform's (platformRepositoryModule).
    single<EntryOutbox> { SqlEntryOutbox(get(), get<SessionManager>().sessionState) }
    single<OutboxSender> {
        OutboxSenderImpl(get(), get(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
    single { OutboxBackgroundRunner(get(), get()) }
    // What the session wipes when one ends or starts: the whole local database.
    single<PersonalData> {
        val database = get<LocalDatabase>()
        PersonalData { database.wipe() }
    }
}

/**
 * The platform's [DeviceInfoProvider], [OutboxScheduler] and [ConnectivityWatcher];
 * Android needs `androidContext` for all three.
 */
expect val platformRepositoryModule: Module

val repositoryModules: List<Module> = listOf(repositoryModule, platformRepositoryModule)
