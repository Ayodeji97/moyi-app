package com.moyi.app.data.session

import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.BearerTokenSource
import com.moyi.app.data.network.PublicHttpClient
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The session. One [SessionManager] for the app, also bound as the authed
 * client's [BearerTokenSource]. Refresh goes over the PUBLIC client.
 */
val sessionModule = module {
    single { SessionManager(store = get(), refreshApi = RefreshControllerApi(get<ApiConfig>().origin, get<PublicHttpClient>().client)) } bind
        BearerTokenSource::class
}

/** The platform's [SecureTokenStore]: Keychain on iOS, Keystore-encrypted file on Android (needs `androidContext`). */
expect val platformSessionModule: Module

val sessionModules: List<Module> = listOf(sessionModule, platformSessionModule)
