package com.moyi.app.feature.auth.navigation

import com.moyi.app.domain.auth.SessionEndReason
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each way a session ends gets the words that are true for it. "Used from two
 * places at once" is only ever said after TOKEN_REUSE_DETECTED.
 */
class RouteAfterSessionEndTest {
    @Test
    fun reuseExplainsOnItsOwnScreen() =
        assertEquals<Any>(SessionEndedRoute, routeAfterSessionEnd(SessionEndReason.ReuseDetected))

    @Test
    fun anInvalidTokenGoesToSignInWithTheNeutralLine() =
        assertEquals<Any>(LoginRoute(sessionEnded = true), routeAfterSessionEnd(SessionEndReason.Invalid))

    @Test
    fun aSignOutThePersonAskedForNeedsNoExplanation() =
        assertEquals<Any>(LoginRoute(), routeAfterSessionEnd(SessionEndReason.SignedOut))
}
