package com.moyi.app.data.repository

import com.moyi.app.data.testing.inMemoryDatabase
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.bond.PendingLeave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [SqlPendingLeaveStore] over a real (in-memory) SQLite: a pending leave survives until removed, for its own person only. */
class SqlPendingLeaveStoreTest {
    private val ada = User("ada-id", "ada@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE)
    private val ben = ada.copy(id = "ben-id", email = "ben@example.test", displayName = "Ben")
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(ada))
    private val database = inMemoryDatabase()
    private val store = SqlPendingLeaveStore(database, session)

    @Test
    fun aPendingLeaveComesBackUntilItIsRemoved() = runTest {
        store.add(PendingLeave("empty", "joined"))
        // The same bond again replaces it: one row per bond.
        store.add(PendingLeave("empty", "joined"))
        assertEquals(listOf(PendingLeave("empty", "joined")), store.all())

        store.remove("empty")

        assertTrue(store.all().isEmpty())
    }

    @Test
    fun anotherPersonSeesNoneOfIt() = runTest {
        store.add(PendingLeave("empty", "joined"))
        session.value = SessionState.SignedIn(ben)
        assertTrue(store.all().isEmpty())
        store.remove("empty")
        session.value = SessionState.SignedIn(ada)
        assertEquals(1, store.all().size, "ben's remove did not touch ada's row")
    }

    @Test
    fun signedOutItKeepsNothing() = runTest {
        session.value = SessionState.SignedOut
        store.add(PendingLeave("empty", "joined"))
        assertEquals(0L, database.rowCount("pending_leave"))
    }
}
