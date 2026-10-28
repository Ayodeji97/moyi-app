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
