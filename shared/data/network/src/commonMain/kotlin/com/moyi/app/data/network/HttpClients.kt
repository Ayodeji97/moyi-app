package com.moyi.app.data.network

import io.ktor.client.HttpClient

/**
 * The client with no Auth plugin: register, verify, resend, login, refresh,
 * logout. Its own type, so nothing that must stay unauthenticated can be
 * handed the authed client by mistake: a wrong password's `401` on login
 * would otherwise start a token refresh.
 */
class PublicHttpClient(val client: HttpClient)

/** The client with the bearer Auth plugin, for every endpoint that needs a signed-in person. */
class AuthedHttpClient(val client: HttpClient)

/**
 * The client for the object storage itself: the presigned PUT of an upload's bytes and
 * the signed GETs that load a photo. No Auth plugin (the URL's signature is the
 * credential; a bearer token must never travel to the storage), no base URL, and **no
 * Logging plugin**, because both URLs are credentials and their query strings are the
 * signature.
 */
class StorageHttpClient(val client: HttpClient)
