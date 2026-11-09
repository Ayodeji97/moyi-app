package com.moyi.app.data.repository

import com.moyi.app.domain.today.Reaction
import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.HttpClientFactory
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.session.PersonalData
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.data.testing.inMemoryDatabase
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.data.testing.storedSession
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.DraftKind
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.OutboxPass
import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.domain.today.QueuedEntry
import com.moyi.app.domain.today.RefusedEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayCache
import com.moyi.app.domain.today.TodayRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * [OutboxSenderImpl]. Two halves:
 * - over the real stack (MockEngine, the authed client and its refresh, SqlEntryOutbox on
 *   SQLite): every try is the same request, and each answer does what it should;
 * - over in-memory fakes on virtual time: the in-process retry's exponential backoff.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OutboxSenderTest {
    private val tap = Instant.parse("2026-09-14T10:59:30.123Z")
    private val queued = QueuedEntry("b1", "You fixed the gate.", "p1", "key-1", tap, "2026-09-14")

    private class Stack(private val answers: MutableList<MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>) {
        val ada = User("7c9e6679-7425-40de-944b-e07fc1f90ae7", "ada@example.test", "Ada", true, UserStatus.ACTIVE)
        val database = inMemoryDatabase()
        val session = MutableStateFlow<SessionState>(SessionState.SignedIn(ada))
        val outbox = SqlEntryOutbox(database, session)
        val drafts = SqlDraftStore(database, session)
        val cache = SqlTodayCache(database, session)
        val entryRequests = mutableListOf<HttpRequestData>()
        var refreshAnswer: MockRequestHandleScope.() -> HttpResponseData = { respondJson(TOKENS) }
        private val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/refresh") {
                refreshAnswer()
            } else {
                entryRequests += request
                answers.removeFirst()(request)
            }
        }
        private val config = ApiConfig("http://localhost:8080/api/v1")
        private val public = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(
            InMemoryTokenStore(storedSession("r1")),
            RefreshControllerApi(config.origin, public.client),
            PersonalData { database.wipe() },
        )
        private val entries = TodayRepositoryImpl(AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager)), config)
        var schedules = 0
        fun sender(scope: kotlinx.coroutines.CoroutineScope) =
            OutboxSenderImpl(outbox, entries, cache, { schedules++ }, { }, scope)

        fun bodies() = entryRequests.map { (it.body as TextContent).text }
        fun keys() = entryRequests.map { it.headers["Idempotency-Key"] }
    }

    private fun stack(vararg answers: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = Stack(answers.toMutableList())

    private val created: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { respondJson(ENTRY, HttpStatusCode.Created) }
    private val offline: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { throw IOException("offline") }

    @Test
    fun theSameKeyAndTheSameBodyIntendedAtIncludedOnEveryTryUntilItIsSent() = runTest {
        val stack = stack(offline, { respondProblem(HttpStatusCode.ServiceUnavailable, "SERVICE_UNAVAILABLE") }, created)
        stack.outbox.enqueue(queued)
        val sender = stack.sender(backgroundScope)

        assertEquals(OutboxPass.Waiting, sender.sendQueued())
        assertEquals(OutboxPass.Waiting, sender.sendQueued())
        assertEquals(OutboxPass.Empty, sender.sendQueued())

        assertEquals(listOf("key-1", "key-1", "key-1"), stack.keys())
        assertEquals(1, stack.bodies().toSet().size, "byte for byte the same body: the server replays it, never writes twice")
        assertTrue("\"intendedAt\":\"2026-09-14T10:59:30.123Z\"" in stack.bodies().first())
        assertTrue(stack.outbox.queued().isEmpty())
    }

    @Test
    fun anIdempotentReplayIsASendLikeAnyOther() = runTest {
        // The first try reached the server and only its answer was lost; this one is replayed.
        val stack = stack({ respondJson(ENTRY, HttpStatusCode.Created) })
        stack.outbox.enqueue(queued)
        assertEquals(OutboxPass.Empty, stack.sender(backgroundScope).sendQueued())
    }

    @Test
    fun aKeyStillInFlightAndARateLimitAreTriedAgainLater() = runTest {
        val stack = stack(
            { respondProblem(HttpStatusCode.Conflict, "IDEMPOTENCY_KEY_IN_FLIGHT") },
            { respondProblem(HttpStatusCode.TooManyRequests, "RATE_LIMITED") },
        )
        stack.outbox.enqueue(queued)
        val sender = stack.sender(backgroundScope)
        assertEquals(OutboxPass.Waiting, sender.sendQueued())
        assertEquals(OutboxPass.Waiting, sender.sendQueued())
        assertEquals(queued, stack.outbox.queuedFor("b1"))
    }

    @Test
    fun alreadyWrittenIsNeverRetriedAndTheWordsGoBackToTheDraft() = runTest {
        val stack = stack({ respondProblem(HttpStatusCode.Conflict, "ENTRY_ALREADY_EXISTS") })
        stack.outbox.enqueue(queued)
        val sender = stack.sender(backgroundScope)

        assertEquals(OutboxPass.Empty, sender.sendQueued())
        assertEquals(OutboxPass.Empty, sender.sendQueued())

        assertEquals(1, stack.entryRequests.size, "a refusal is an answer: asked once")
        assertEquals("You fixed the gate.", stack.drafts.read("b1", DraftKind.NewEntry)?.text)
        assertEquals(RefusedEntry("b1", OutboxRefusal.AlreadyWritten, "2026-09-14"), stack.outbox.refusalFor("b1"))
    }

    @Test
    fun aDayThatOpenedMeanwhileIsAlreadyWrittenToo() = runTest {
        // Seen on the emulator: both had written (one from another phone), so the day revealed.
        val stack = stack({ respondProblem(HttpStatusCode.Conflict, "DAY_CLOSED") })
        stack.outbox.enqueue(queued)
        assertEquals(OutboxPass.Empty, stack.sender(backgroundScope).sendQueued())
        assertEquals(OutboxRefusal.AlreadyWritten, stack.outbox.refusalFor("b1")?.reason)
        assertEquals("You fixed the gate.", stack.drafts.read("b1", DraftKind.NewEntry)?.text)
    }

    @Test
    fun aValidationRefusalIsNotAcceptedAndKeepsTheWordsToo() = runTest {
        val stack = stack({ respondProblem(HttpStatusCode.UnprocessableEntity, "VALIDATION_FAILED") })
        stack.outbox.enqueue(queued)
        assertEquals(OutboxPass.Empty, stack.sender(backgroundScope).sendQueued())
        assertEquals(OutboxRefusal.NotAccepted, stack.outbox.refusalFor("b1")?.reason)
        assertEquals("You fixed the gate.", stack.drafts.read("b1", DraftKind.NewEntry)?.text)
    }

    @Test
    fun a401IsRefreshedOnceAndTheSameRequestSentAgain() = runTest {
        val stack = stack({ respondProblem(HttpStatusCode.Unauthorized, "UNAUTHORIZED", bearerChallenge = true) }, created)
        stack.outbox.enqueue(queued)
        assertEquals(OutboxPass.Empty, stack.sender(backgroundScope).sendQueued())
        assertEquals(listOf("key-1", "key-1"), stack.keys())
        assertEquals(1, stack.bodies().toSet().size)
    }

    @Test
    fun aRefreshTheServerRefusesEndsTheSessionAndItsWipeTakesTheQueue() = runTest {
        // Involuntary: privacy wins over the unsent words (the session wipe is total).
        val stack = stack({ respondProblem(HttpStatusCode.Unauthorized, "UNAUTHORIZED", bearerChallenge = true) })
        stack.refreshAnswer = { respondProblem(HttpStatusCode.Unauthorized, "REFRESH_TOKEN_INVALID") }
        stack.outbox.enqueue(queued)

        assertEquals(OutboxPass.SignedOut, stack.sender(backgroundScope).sendQueued())
        assertEquals(0L, stack.database.rowCount("outbox_entry"))
    }

    @Test
    fun retrySoonAsksThePlatformToTryInTheBackgroundFirst() = runTest {
        val stack = stack(created)
        stack.outbox.enqueue(queued)
        val sender = stack.sender(backgroundScope)
        sender.retrySoon()
        // SQLite runs on the IO dispatcher: wait for the real work, not virtual time.
        repeatUntil { stack.outbox.queued().isEmpty() }
        assertEquals(1, stack.schedules)
    }

    // ---- The in-process retry, on virtual time ----

    @Test
    fun theRetryBacksOffExponentiallyWithTheSameKeyAndIntendedAtEveryTime() = runTest {
        val entries = ScriptedEntries(failures = 4)
        val sender = OutboxSenderImpl(MemoryOutbox(queued), entries, NoCache, {}, {}, backgroundScope, Backoff(initial = 2.seconds, max = 10.seconds))

        sender.retrySoon()
        runCurrent()
        advanceTimeBy(60.seconds)

        // 0, then +2, +4, +8, and +10 (the cap): 0, 2, 6, 14, 24 seconds.
        assertEquals(listOf(0L, 2_000L, 6_000L, 14_000L, 24_000L), entries.at)
        assertEquals(setOf("key-1" to tap), entries.sent.toSet())
    }

    @Test
    fun aConnectionComingBackCutsTheWaitShortAndStartsTheBackoffAgain() = runTest {
        val entries = ScriptedEntries(failures = 3)
        var online: () -> Unit = {}
        val sender = OutboxSenderImpl(MemoryOutbox(queued), entries, NoCache, {}, { online = it }, backgroundScope, Backoff(initial = 2.seconds, max = 1.minutes))
        sender.start()

        sender.retrySoon()
        runCurrent()
        advanceTimeBy(2_500) // tries at 0 and 2 s; the next is due at 6 s
        online()
        runCurrent()
        assertEquals(listOf(0L, 2_000L, 2_500L), entries.at, "the network came back: try now, not at 6 s")
        advanceTimeBy(2_001)
        assertEquals(4_500L, entries.at.last(), "and the wait starts again from 2 s")
    }

    @Test
    fun nothingQueuedMeansNoRequestAndNoBackgroundWork() = runTest {
        val entries = ScriptedEntries(failures = 0)
        var schedules = 0
        val sender = OutboxSenderImpl(MemoryOutbox(), entries, NoCache, { schedules++ }, {}, backgroundScope)
        sender.retrySoon()
        advanceTimeBy(10.minutes)
        assertTrue(entries.at.isEmpty())
        assertEquals(0, schedules)
    }

    @Test
    fun theChangesFlowTellsTodayWhenSomethingWasSent() = runTest {
        val sender = OutboxSenderImpl(MemoryOutbox(queued), ScriptedEntries(failures = 0), NoCache, {}, {}, backgroundScope)
        val changes = mutableListOf<Unit>()
        backgroundScope.launch { sender.changes.collect { changes += it } }
        runCurrent()
        sender.sendQueued()
        runCurrent()
        assertEquals(1, changes.size)
    }

    /** A [TodayRepository] that fails [failures] times with no connection, recording each try's virtual time. */
    private inner class ScriptedEntries(private var failures: Int) : TodayRepository {
        val at = mutableListOf<Long>()
        val sent = mutableListOf<Pair<String, Instant?>>()

        override suspend fun submitEntry(
            bondId: String,
            text: String,
            promptId: String?,
            idempotencyKey: String,
            intendedAt: Instant?,
            imageMediaId: String?,
            voiceMediaId: String?,
        ): Result<MyEntry, DataError.Network> {
            at += currentCoroutineContext()[TestCoroutineScheduler]?.currentTime ?: -1
            sent += idempotencyKey to intendedAt
            if (failures-- > 0) return Result.Failure(DataError.Network.NoInternet)
            return Result.Success(MyEntry("e1", text, EntryStatus.SUBMITTED))
        }

        override suspend fun today(bondId: String): Result<Today, DataError.Network> = error("not used")
        override suspend fun editEntry(entryId: String, text: String, idempotencyKey: String, imageMediaId: String?, voiceMediaId: String?) = error("not used")
        override suspend fun deleteEntry(entryId: String): EmptyResult<DataError.Network> = error("not used")
        override suspend fun setReaction(entryId: String, reaction: Reaction?): EmptyResult<DataError.Network> = error("not used")
    }

    /** An [EntryOutbox] in a list: no IO dispatcher, so virtual time is the only clock. */
    private class MemoryOutbox(vararg initial: QueuedEntry) : EntryOutbox {
        val queue = initial.toMutableList()
        override suspend fun enqueue(entry: QueuedEntry) {
            queue += entry
        }
        override suspend fun queued(): List<QueuedEntry> = queue.toList()
        override suspend fun queuedFor(bondId: String): QueuedEntry? = queue.firstOrNull { it.bondId == bondId }
        override suspend fun remove(idempotencyKey: String) {
            queue.removeAll { it.idempotencyKey == idempotencyKey }
        }
        override suspend fun refuse(entry: QueuedEntry, reason: OutboxRefusal) {
            queue.remove(entry)
        }
        override suspend fun refusalFor(bondId: String): RefusedEntry? = null
        override suspend fun clearRefusal(bondId: String) = Unit
    }

    private suspend fun repeatUntil(condition: suspend () -> Boolean) {
        repeat(200) {
            if (condition()) return
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(10) }
        }
        error("condition never held")
    }

    /** No cached day: the virtual-time tests need nothing on the IO dispatcher. */
    private object NoCache : TodayCache {
        var clears = 0
        override suspend fun read(): CachedDay? = null
        override suspend fun write(day: CachedDay) = Unit
        override suspend fun clear() {
            clears++
        }
    }

    @Test
    fun aSentEntryForgetsTheCachedDayTakenBeforeIt() = runTest {
        NoCache.clears = 0
        val sender = OutboxSenderImpl(MemoryOutbox(queued), ScriptedEntries(failures = 1), NoCache, {}, {}, backgroundScope)
        sender.sendQueued()
        assertEquals(0, NoCache.clears, "nothing changed: the cached day stands")
        sender.sendQueued()
        assertEquals(1, NoCache.clears, "a launch whose refresh fails must not draw Write under a sent entry")
    }

    private companion object {
        const val TOKENS = """{"accessToken":"a1","expiresIn":900,"refreshToken":"r2","user":{"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7","email":"ada@example.test","displayName":"Ada","locale":"en","emailVerified":true,"status":"ACTIVE"}}"""
        const val ENTRY = """{"id":"e1","bondId":"b1","date":"2026-09-14","authorMemberId":"m1","text":"You fixed the gate.",""" +
            """"status":"SUBMITTED","createdAt":"2026-09-14T11:00:00Z","intendedAt":"2026-09-14T10:59:30.123Z","favourited":false,"reaction":null}"""
    }
}
