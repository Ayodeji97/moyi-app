package com.moyi.app.feature.auth.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The typed routes carry an email address, which may hold a `+` (sub-addressing)
 * or non-ASCII letters. Here, the routes' generated serializers keep it byte for
 * byte. The navigation leg (navigate() URL-encodes the arguments, toRoute()
 * decodes them) is proven in iosTest/NavControllerRouteTest: on Android both
 * need a Context and a real Bundle, which host tests do not have, so the device
 * run covers Android.
 */
class AuthRouteTest {
    private val email = "a+b@exämple.com"

    @Test
    fun verifyRouteSurvivesItsSerializer() {
        val route = VerifyRoute(email)
        assertEquals(route, Json.decodeFromString<VerifyRoute>(Json.encodeToString(route)))
    }

    @Test
    fun loginRouteSurvivesItsSerializer() {
        val route = LoginRoute(email = email, sessionEnded = true)
        assertEquals(route, Json.decodeFromString<LoginRoute>(Json.encodeToString(route)))
    }
}
