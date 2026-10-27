package com.moyi.app.data.network

import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module

/**
 * The HttpClients and where they point. One engine for the app, shared by
 * both clients. The authed client needs a [BearerTokenSource], which the
 * session module provides.
 */
val networkModule = module {
    single { ApiConfig.fromBuild() }
    single<HttpClientEngine> { platformEngine() }
    single { PublicHttpClient(HttpClientFactory.createPublicClient(get<HttpClientEngine>())) }
    single { AuthedHttpClient(HttpClientFactory.createAuthedClient(get<HttpClientEngine>(), get<BearerTokenSource>())) }
}
