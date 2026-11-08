package com.moyi.app.data.repository

import com.moyi.app.data.testing.inMemoryDatabase
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.today.Draft
import com.moyi.app.domain.today.DraftKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** [SqlDraftStore] over a real (in-memory) SQLite: a draft comes back to its own person, bond and kind only. */
class SqlDraftStoreTest {
    private val ada = User("ada-id", "ada@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE)
    private val ben = ada.copy(id = "ben-id", email = "ben@example.test", displayName = "Ben")
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(ada))
    private val database = inMemoryDatabase()
    private val store = SqlDraftStore(database, session)

    private val newEntry = Draft("bond-1", DraftKind.NewEntry, "You made the tea.", dayDate = "2026-09-14")
    private val edit = Draft("bond-1", DraftKind.Edit, "You made the tea, again.", dayDate = "2026-09-14", entryId = "e1")

    @Test
    fun aDraftComesBackAsItWent() = runTest {
        store.write(newEntry)
        store.write(edit)
        assertEquals(newEntry, store.read("bond-1", DraftKind.NewEntry))
        assertEquals(edit, store.read("bond-1", DraftKind.Edit))
    }

    @Test
    fun theNewEntryAndTheEditAreKeptApart() = runTest {
        store.write(newEntry)
        store.write(edit)
        store.write(newEntry.copy(text = "You listened."))

        assertEquals("You listened.", store.read("bond-1", DraftKind.NewEntry)?.text)
        assertEquals(edit, store.read("bond-1", DraftKind.Edit), "a new-entry write must not touch the edit")

        store.delete("bond-1", DraftKind.Edit)
        assertNull(store.read("bond-1", DraftKind.Edit))
        assertEquals("You listened.", store.read("bond-1", DraftKind.NewEntry)?.text, "deleting the edit keeps the new entry")
    }

    @Test
    fun aNewerWriteReplacesTheDraftOfItsKind() = runTest {
        store.write(newEntry)
        store.write(newEntry.copy(text = "You listened.", dayDate = "2026-09-15"))
        assertEquals(1L, database.rowCount("draft"))
        assertEquals(newEntry.copy(text = "You listened.", dayDate = "2026-09-15"), store.read("bond-1", DraftKind.NewEntry))
    }

    @Test
    fun aDraftIsReadOnlyByThePersonWhoWroteIt() = runTest {
        store.write(newEntry)

        session.value = SessionState.SignedIn(ben)
        assertNull(store.read("bond-1", DraftKind.NewEntry), "Ben must never be shown Ada's words")
        store.delete("bond-1", DraftKind.NewEntry)

        session.value = SessionState.SignedIn(ada)
        assertEquals(newEntry, store.read("bond-1", DraftKind.NewEntry), "Ben's delete must not reach Ada's draft")

        session.value = SessionState.SignedOut
        assertNull(store.read("bond-1", DraftKind.NewEntry))
    }

    @Test
    fun aDraftIsKeptPerBond() = runTest {
        store.write(newEntry)
        assertNull(store.read("bond-2", DraftKind.NewEntry))
    }

    @Test
    fun nobodySignedInWritesNothing() = runTest {
        session.value = SessionState.Unknown
        store.write(newEntry)
        assertEquals(0L, database.rowCount("draft"))
    }

    @Test
    fun aRowThisBuildCannotReadIsNoDraft() = runTest {
        database.io { draftQueries.upsert(newEntry.toRow(ada.id).copy(kind = "SOMETHING_NEW")) }
        assertNull(store.read("bond-1", DraftKind.NewEntry))
    }

    @Test
    fun theWordsNeverReachToString() {
        assertFalse("tea" in newEntry.toString())
    }
}
