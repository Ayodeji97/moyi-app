package com.moyi.app.data.network

import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The HttpClients and where they point. One engine for the app, shared by
 * both clients. The authed client needs a [BearerTokenSource], which the
 * session module provides. [apiConfig] comes from the app, which knows its build type.
 */
fun networkModule(apiConfig: ApiConfig): Module = module {
    single { apiConfig }
    single<HttpClientEngine> { platformEngine() }
    single { PublicHttpClient(HttpClientFactory.createPublicClient(get<HttpClientEngine>())) }
    single { StorageHttpClient(HttpClientFactory.createStorageClient(get<HttpClientEngine>())) }
    single { AuthedHttpClient(HttpClientFactory.createAuthedClient(get<HttpClientEngine>(), get<BearerTokenSource>())) }
}
