package com.moyi.app.data.repository

import com.moyi.app.data.testing.inMemoryDatabase
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.PendingProposal
import com.moyi.app.domain.bond.PendingRequest
import com.moyi.app.domain.bond.SharedSettingNotice
import com.moyi.app.domain.bond.SharedSettingsSnapshot
import com.moyi.app.domain.bond.TimeOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** [SqlSharedSettingsStore] over a real (in-memory) SQLite: everything it keeps comes back as it went in, for its own person only. */
class SqlSharedSettingsStoreTest {
    private val ada = User("ada-id", "ada@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE)
    private val ben = ada.copy(id = "ben-id", email = "ben@example.test", displayName = "Ben")
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(ada))
    private val store = SqlSharedSettingsStore(inMemoryDatabase(), session)

    private val snapshot = SharedSettingsSnapshot(
        bondId = "b1",
        status = BondStatus.ACTIVE,
        name = "Us",
        type = BondType.FAMILY,
        anchorTimezone = "Africa/Lagos",
        revealTime = TimeOfDay(21, 0),
        strictMode = true,
        semanticSearchEnabled = false,
        deletionScheduled = false,
        timezoneProposal = PendingProposal("p1", "Europe/London", byMe = true, expiresAt = Instant.parse("2026-11-20T10:00:00Z")),
        deletionRequest = PendingRequest(byMe = false, expiresAt = Instant.parse("2026-11-21T10:00:00Z")),
        semanticSearchRequest = PendingRequest(byMe = true, expiresAt = Instant.parse("2026-11-22T10:00:00Z")),
    )

    private val everyNotice = listOf(
        SharedSettingNotice.TimezoneAgreed("Europe/London"),
        SharedSettingNotice.TimezoneKept("Africa/Lagos"),
        SharedSettingNotice.TimezoneWithdrawn("Europe/London"),
        SharedSettingNotice.DeletionAgreed,
        SharedSettingNotice.DeletionKeptOpen,
        SharedSettingNotice.DeletionWithdrawn,
        SharedSettingNotice.DeletionCalledOff,
        SharedSettingNotice.SemanticSearchAgreed,
        SharedSettingNotice.SemanticSearchKeptOff,
        SharedSettingNotice.SemanticSearchWithdrawn,
        SharedSettingNotice.SemanticSearchTurnedOff,
        SharedSettingNotice.SemanticSearchAsked,
        SharedSettingNotice.NameChanged("Tea"),
        SharedSettingNotice.RevealTimeChanged(TimeOfDay(20, 30)),
        SharedSettingNotice.RevealTimeChanged(null),
        SharedSettingNotice.RestDaysChanged(on = false),
        SharedSettingNotice.TypeChanged(BondType.PARENT_CHILD),
    )

    @Test
    fun aSnapshotAndEveryKindOfNoteComeBackAsTheyWentIn() = runTest {
        store.save(snapshot, everyNotice)

        assertEquals(snapshot, store.snapshot("b1"))
        assertEquals(everyNotice, store.notices("b1").map { it.notice }, "oldest first")
    }

    @Test
    fun aSnapshotWithNothingWaitingComesBackWithoutIt() = runTest {
        val plain = snapshot.copy(revealTime = null, timezoneProposal = null, deletionRequest = null, semanticSearchRequest = null)
        store.save(plain, emptyList())

        assertEquals(plain, store.snapshot("b1"))
    }

    @Test
    fun aSecondSaveReplacesTheSnapshotAndAddsToTheNotes() = runTest {
        store.save(snapshot, listOf(SharedSettingNotice.NameChanged("A")))
        store.save(snapshot.copy(name = "B"), listOf(SharedSettingNotice.NameChanged("B")))

        assertEquals("B", store.snapshot("b1")?.name)
        assertEquals(2, store.notices("b1").size)
    }

    @Test
    fun aRemovedNoteIsGone() = runTest {
        store.save(snapshot, listOf(SharedSettingNotice.DeletionAgreed, SharedSettingNotice.DeletionWithdrawn))
        val first = store.notices("b1").first()

        store.removeNotice("b1", first.id)

        assertEquals(listOf(SharedSettingNotice.DeletionWithdrawn), store.notices("b1").map { it.notice })
    }

    @Test
    fun anotherPersonSeesNoneOfIt() = runTest {
        store.save(snapshot, listOf(SharedSettingNotice.DeletionAgreed))
        session.value = SessionState.SignedIn(ben)

        assertNull(store.snapshot("b1"))
        assertTrue(store.notices("b1").isEmpty())
    }

    @Test
    fun signedOutItKeepsNothing() = runTest {
        session.value = SessionState.SignedOut
        store.save(snapshot, listOf(SharedSettingNotice.DeletionAgreed))
        session.value = SessionState.SignedIn(ada)

        assertNull(store.snapshot("b1"))
    }
}
