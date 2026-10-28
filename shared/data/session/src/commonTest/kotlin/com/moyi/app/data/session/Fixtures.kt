@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.moyi.app.data.session

import com.moyi.app.data.testing.USER_ID
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

val ada = User(USER_ID, "ada@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE)

fun grant(access: String, refresh: String) = SessionGrant(access, refresh, ada)

/** Collects every session event for the rest of the test. */
fun TestScope.collectEvents(manager: SessionManager): List<SessionEvent> {
    val events = mutableListOf<SessionEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { manager.events.toList(events) }
    return events
}
