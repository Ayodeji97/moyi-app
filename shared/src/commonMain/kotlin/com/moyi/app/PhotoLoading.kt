package com.moyi.app

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import io.ktor.client.HttpClient

/**
 * Coil's one ImageLoader (doc 13 §3), for every photo on an EntryCard.
 *
 * - **Network through the storage client** ([com.moyi.app.data.network.StorageHttpClient]):
 *   no bearer token (the signed URL is the credential, and a token must never travel to
 *   the storage) and no HTTP logging (the URL's query is its signature).
 * - **No disk cache.** The local database is encrypted at rest (SQLCipher, #40); a disk
 *   cache would keep the partner's photos as plain files beside it. Photos are kept in
 *   memory only, and loaded again after the app restarts (owner's ruling asked, §40).
 * - **No service loader**: only the fetcher configured here, never one found on the
 *   classpath with a client of its own.
 */
internal fun moyiImageLoader(context: PlatformContext, storage: HttpClient): ImageLoader =
    ImageLoader.Builder(context)
        .serviceLoaderEnabled(false)
        .components { add(KtorNetworkFetcherFactory(storage)) }
        .diskCache(null)
        .build()
