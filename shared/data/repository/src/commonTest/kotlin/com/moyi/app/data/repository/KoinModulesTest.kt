package com.moyi.app.data.repository

import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.BearerTokenSource
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.network.networkModule
import com.moyi.app.data.session.SecureTokenStore
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.session.sessionModule
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.bond.BondRepository
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Resolves the common modules the way the app will, with the two platform
 * bindings (secure store, device info) replaced by test ones. A missing or
 * mis-typed definition fails here, not at the first screen.
 */
class KoinModulesTest {
    private val testPlatform = module {
        single<SecureTokenStore> { InMemoryTokenStore() }
        single<DeviceInfoProvider> { DeviceInfoProvider { DeviceInfo("IOS", "1.0", "26.0") } }
    }

    @Test
    fun everyDefinitionResolvesAndTheSessionIsShared() {
        val koin = koinApplication { modules(networkModule, sessionModule, repositoryModule, testPlatform) }.koin
        try {
            koin.get<AuthRepository>()
            koin.get<AccountRepository>()
            koin.get<BondRepository>()
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
