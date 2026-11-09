package com.moyi.app.data.repository

import app.cash.sqldelight.db.SqlDriver
import com.moyi.app.api.models.DeviceRequest
import com.moyi.app.data.database.LocalDatabase
import com.moyi.app.data.database.databaseModule
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.BearerTokenSource
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.network.StorageHttpClient
import com.moyi.app.data.network.networkModule
import com.moyi.app.data.session.PersonalData
import com.moyi.app.data.session.SecureTokenStore
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.session.sessionModule
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.data.testing.inMemoryDriver
import com.moyi.app.domain.archive.ArchiveRepository
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.media.MediaRepository
import com.moyi.app.domain.streak.StreakRepository
import com.moyi.app.domain.today.DraftStore
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.domain.today.TodayCache
import com.moyi.app.domain.today.TodayRepository
import io.ktor.client.engine.HttpClientEngine
import kotlin.test.Test
import kotlin.test.assertSame
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Resolves the common modules the way the app will, with the three platform
 * bindings (secure store, device info, SQLite driver) replaced by test ones. A missing or
 * mis-typed definition fails here, not at the first screen.
 */
class KoinModulesTest {
    private val testPlatform = module {
        single<SecureTokenStore> { InMemoryTokenStore() }
        single<DeviceInfoProvider> { DeviceInfoProvider { DeviceInfo(DeviceRequest.Platform.IOS, "1.0", "26.0") } }
        single<SqlDriver> { inMemoryDriver() }
        single<OutboxScheduler> { OutboxScheduler {} }
        single<ConnectivityWatcher> { ConnectivityWatcher {} }
    }

    @Test
    fun everyDefinitionResolvesAndTheSessionIsShared() {
        val koin = koinApplication { modules(networkModule, sessionModule, databaseModule, repositoryModule, testPlatform) }.koin
        try {
            koin.get<AuthRepository>()
            koin.get<AccountRepository>()
            koin.get<BondRepository>()
            koin.get<TodayRepository>()
            koin.get<MediaRepository>()
            koin.get<StorageHttpClient>()
            koin.get<ArchiveRepository>()
            koin.get<StreakRepository>()
            koin.get<TodayCache>()
            koin.get<DraftStore>()
            koin.get<EntryOutbox>()
            koin.get<OutboxSender>()
            koin.get<OutboxBackgroundRunner>()
            koin.get<LocalDatabase>()
            koin.get<PersonalData>()
            koin.get<PublicHttpClient>()
            koin.get<AuthedHttpClient>()
            assertSame(koin.get<SessionManager>(), koin.get<BearerTokenSource>())
            assertSame(koin.get<HttpClientEngine>(), koin.get<HttpClientEngine>())
        } finally {
            koin.get<HttpClientEngine>().close()
            koin.close()
        }
    }
}
