package com.moyi.app.data.repository

import com.moyi.app.data.testing.inMemoryDatabase
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.today.Draft
import com.moyi.app.domain.today.DraftKind
import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.domain.today.QueuedEntry
import com.moyi.app.domain.today.RefusedEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** [SqlEntryOutbox] over a real (in-memory) SQLite: its own person only, and a refusal never loses words. */
class SqlEntryOutboxTest {
    private val ada = User("ada-id", "ada@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE)
    private val ben = ada.copy(id = "ben-id", email = "ben@example.test", displayName = "Ben")
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(ada))
    private val database = inMemoryDatabase()
    private val outbox = SqlEntryOutbox(database, session)
    private val drafts = SqlDraftStore(database, session)

    private val entry = QueuedEntry(
        bondId = "bond-1",
        text = "You fixed the gate.",
        promptId = "prompt-1",
        idempotencyKey = "key-1",
        intendedAt = Instant.parse("2026-09-14T10:59:30.123Z"),
        dayDate = "2026-09-14",
    )

    @Test
    fun aQueuedEntryComesBackExactlyAsItWentKeyAndIntendedAtIncluded() = runTest {
        outbox.enqueue(entry)
        assertEquals(listOf(entry), outbox.queued())
        assertEquals(entry, outbox.queuedFor("bond-1"))
        assertNull(outbox.queuedFor("bond-2"))
    }

    @Test
    fun itIsReadOnlyByThePersonWhoQueuedItAndNobodyWhenSignedOut() = runTest {
        outbox.enqueue(entry)
        session.value = SessionState.SignedIn(ben)
        assertTrue(outbox.queued().isEmpty())
        session.value = SessionState.SignedOut
        assertTrue(outbox.queued().isEmpty())
        outbox.enqueue(entry.copy(idempotencyKey = "key-2"))
        assertEquals(1L, database.rowCount("outbox_entry"), "signed out, nothing is kept")
    }

    @Test
    fun removeTakesOnlyThatKey() = runTest {
        outbox.enqueue(entry)
        outbox.enqueue(entry.copy(bondId = "bond-2", idempotencyKey = "key-2"))
        outbox.remove("key-1")
        assertEquals(listOf("key-2"), outbox.queued().map { it.idempotencyKey })
    }

    @Test
    fun aRefusalMovesTheWordsToTheDraftAndKeepsOnlyWhyWithoutTheWords() = runTest {
        outbox.enqueue(entry)

        outbox.refuse(entry, OutboxRefusal.NotAccepted)

        assertTrue(outbox.queued().isEmpty(), "never retried")
        assertEquals(Draft("bond-1", DraftKind.NewEntry, "You fixed the gate.", "2026-09-14"), drafts.read("bond-1", DraftKind.NewEntry))
        assertEquals(RefusedEntry("bond-1", OutboxRefusal.NotAccepted, "2026-09-14"), outbox.refusalFor("bond-1"))
        val kept = database.io { outboxQueries.refusedForBond("ada-id", "bond-1").executeAsOne() }
        assertEquals("", kept.text, "the refusal marker holds no words: they live in the draft")

        outbox.clearRefusal("bond-1")
        assertNull(outbox.refusalFor("bond-1"))
        assertEquals(0L, database.rowCount("outbox_entry"))
    }

    @Test
    fun aRefusalNeverWritesOverADraftAlreadyThere() = runTest {
        drafts.write(Draft("bond-1", DraftKind.NewEntry, "Words begun since.", "2026-09-15"))
        outbox.enqueue(entry)

        outbox.refuse(entry, OutboxRefusal.AlreadyWritten)

        val draft = drafts.read("bond-1", DraftKind.NewEntry)
        assertEquals("You fixed the gate.\n\nWords begun since.", draft?.text)
        assertEquals("2026-09-15", draft?.dayDate)
        assertEquals(OutboxRefusal.AlreadyWritten, outbox.refusalFor("bond-1")?.reason)
    }

    @Test
    fun theSessionWipeTakesTheQueueAndTheRefusals() = runTest {
        outbox.enqueue(entry)
        outbox.enqueue(entry.copy(idempotencyKey = "key-2", bondId = "bond-2"))
        outbox.refuse(entry, OutboxRefusal.NotAccepted)

        database.wipe()

        assertTrue(outbox.queued().isEmpty())
        assertNull(outbox.refusalFor("bond-1"))
        assertEquals(0L, database.rowCount("outbox_entry"))
    }
}
