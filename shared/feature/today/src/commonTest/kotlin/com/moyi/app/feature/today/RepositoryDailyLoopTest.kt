package com.moyi.app.feature.today

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.FieldError
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.OutboxRefusal
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.QueuedEntry
import com.moyi.app.domain.today.RefusedEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.DeleteResult
import com.moyi.app.feature.today.common.QueuedDay
import com.moyi.app.feature.today.common.RepositoryDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryDailyLoopTest {
    private val bonds = FakeBonds()
    private val entries = FakeTodayEntries()
    private val cache = FakeTodayCache()
    private val outbox = FakeOutbox()
    private val archiveCache = FakeArchiveCache()
    private val loop = RepositoryDailyLoop(bonds, entries, cache, outbox, outbox, FixedClock(NOON_ON_THE_DAY), archiveCache)

    private val ready get() = assertIs<DailyLoopState.Ready>(loop.state.value).day

    @Test
    fun startsOnTheBareSurfaceThenLoadsTheFirstOpenBondsDay() = runTest {
        assertEquals(DailyLoopState.Loading, loop.state.value)
        bonds.result = Result.Success(listOf(ACTIVE.copy(id = "old", status = BondStatus.ARCHIVED), ACTIVE))
        loop.refresh()
        assertEquals("bond-1", ready.bondId)
        assertEquals(listOf("bond-1"), entries.todayCalls)
        assertEquals(TodayStatus.NotWritten, ready.status)
        assertEquals(PROMPT, ready.prompt)
    }

    @Test
    fun noOpenBondIsSaidAsSuch() = runTest {
        bonds.result = Result.Success(listOf(ACTIVE.copy(status = BondStatus.ARCHIVED)))
        loop.refresh()
        assertEquals(DailyLoopState.NoOpenBond, loop.state.value)
        assertEquals(emptyList(), entries.todayCalls)
    }

    @Test
    fun aFailedFirstLoadIsTheErrorAndAFailedRefreshKeepsTheDay() = runTest {
        bonds.result = Result.Failure(DataError.Network.NoInternet)
        loop.refresh()
        assertEquals(DailyLoopState.Failed, loop.state.value)

        bonds.result = Result.Success(listOf(ACTIVE))
        loop.refresh()
        assertEquals(TodayStatus.NotWritten, ready.status)

        entries.todayResult = Result.Failure(DataError.Network.Timeout)
        loop.refresh()
        assertEquals(TodayStatus.NotWritten, ready.status, "a refresh that failed must not blank the day")
    }

    @Test
    fun aDayThatCannotBeShownHonestlyIsAFailedLoad() = runTest {
        entries.todayResult = Result.Success(today().copy(date = "not a date"))
        loop.refresh()
        assertEquals(DailyLoopState.Failed, loop.state.value)
    }

    @Test
    fun aSendShowsWaitingAtOnceEvenIfTheRefreshAfterItFails() = runTest {
        loop.refresh()
        entries.todayResult = Result.Failure(DataError.Network.NoInternet)
        assertEquals(WriteResult.Saved, loop.submit(MINE, PROMPT.id, "key-1", NOON_ON_THE_DAY))
        assertEquals(TodayStatus.Waiting, ready.status)
        assertEquals(MINE, ready.myEntry)
    }

    @Test
    fun aRefusedSendFailsAndAConflictCatchesTodayUp() = runTest {
        loop.refresh()
        val before = entries.todayCalls.size
        entries.submitResult = Result.Failure(api(422, ApiErrorCode.VALIDATION_FAILED))
        assertEquals(WriteResult.Failed, loop.submit(MINE, null, "key-1", NOON_ON_THE_DAY))
        assertEquals(before, entries.todayCalls.size, "a validation refusal: nothing to catch up on")
        assertTrue(outbox.queue.isEmpty(), "a refusal is an answer: never queued")

        // Written from the other phone: Today shows what the server holds.
        entries.submitResult = Result.Failure(api(409, ApiErrorCode.ENTRY_ALREADY_EXISTS))
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        assertEquals(WriteResult.Failed, loop.submit(MINE, null, "key-2", NOON_ON_THE_DAY))
        assertEquals(TodayStatus.Waiting, ready.status)
    }

    @Test
    fun anEditBeforeAnythingIsWrittenSendsNothing() = runTest {
        loop.refresh()
        assertEquals(WriteResult.Failed, loop.edit("words", "key-1"))
    }

    @Test
    fun deletingBeforeTheRevealIsOptimisticAndAFailureRestoresTheEntry() = runTest {
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        loop.refresh()
        val gate = CompletableDeferred<Unit>()
        entries.deleteGate = gate
        entries.deleteResult = Result.Failure(DataError.Network.NoInternet)

        val delete = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            assertEquals(DeleteResult.Failed, loop.deleteBeforeReveal())
        }
        assertEquals(TodayStatus.NotWritten, ready.status, "not written at once, before the server answers")

        gate.complete(Unit)
        delete.join()
        assertEquals(TodayStatus.Waiting, ready.status)
        assertEquals(MINE, ready.myEntry)
        assertEquals(listOf("entry-1"), entries.deleted)
    }

    @Test
    fun deletingAfterTheRevealShowsTheTombstoneOnlyOnceTheServerAgrees() = runTest {
        val theirs = PartnerEntry.Revealed("entry-2", THEIRS)
        entries.todayResult = Result.Success(today(BondDayStatus.REVEALED, my = MY_ENTRY, theirs = theirs))
        loop.refresh()
        val gate = CompletableDeferred<Unit>()
        entries.deleteGate = gate
        // The refresh after the delete fails, so what is shown is the loop's own tombstone.
        entries.todayResult = Result.Failure(DataError.Network.NoInternet)

        val delete = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            assertEquals(DeleteResult.Deleted, loop.deleteAfterReveal())
        }
        assertEquals(TodayStatus.Revealed, ready.status, "irreversible: nothing changes before the 204")

        gate.complete(Unit)
        delete.join()
        assertEquals(TodayStatus.Tombstone, ready.status)
        assertEquals(null, ready.myEntry)
        assertEquals(THEIRS, ready.partnerEntry, "deleting mine never touches theirs")
    }

    @Test
    fun aDeleteAlreadyGoneIsNotAFailure() = runTest {
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        loop.refresh()
        entries.deleteResult = Result.Failure(api(404, ApiErrorCode.Unknown))
        entries.todayResult = Result.Success(today(BondDayStatus.OPEN))
        assertEquals(DeleteResult.Gone, loop.deleteBeforeReveal())
        assertEquals(TodayStatus.NotWritten, ready.status)
    }

    @Test
    fun aLoadStillInFlightAtSignOutNeverPublishesIntoTheNextSession() = runTest {
        loop.refresh()
        val gate = CompletableDeferred<Unit>()
        entries.todayGate = gate
        val inFlight = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { loop.refresh() }
        loop.clear()
        entries.todayResult = Result.Success(today(BondDayStatus.REVEALED, my = MY_ENTRY))
        gate.complete(Unit)
        inFlight.join()
        assertEquals(DailyLoopState.Loading, loop.state.value)
    }

    @Test
    fun aRefreshAfterTheFirstAsksForTheBondsAndTheDayTogether() = runTest {
        loop.refresh()
        loop.refresh()
        assertEquals(2, bonds.calls)
        assertEquals(listOf("bond-1", "bond-1"), entries.todayCalls)
    }

    // ---- The outbox (doc 13 §5) ----

    @Test
    fun aSendWithNoAnswerIsQueuedWithItsKeyAndIntendedAtAndTodayShowsQueued() = runTest {
        loop.refresh()
        entries.submitResult = Result.Failure(DataError.Network.NoInternet)
        val tap = Instant.parse("2026-09-14T10:59:30.123Z")

        assertEquals(WriteResult.Queued, loop.submit(MINE, PROMPT.id, "key-1", tap))

        val queued = outbox.queue.single()
        assertEquals("key-1", queued.idempotencyKey)
        assertEquals(tap, queued.intendedAt)
        assertEquals(PROMPT.id, queued.promptId)
        assertEquals("2026-09-14", queued.dayDate)
        assertEquals(tap, entries.submitted.single().intendedAt, "the first try already carries intendedAt: one body for every try")
        assertEquals(1, outbox.retries, "the sender is asked to try again")
        assertEquals(TodayStatus.Queued, ready.status)
        assertEquals(MINE, ready.myEntry)
        assertEquals(QueuedDay.ThisDay(DAY), ready.queuedDay)
    }

    @Test
    fun aTimeoutAndTheServersOwnFailureQueueTooButAnEditNeverDoes() = runTest {
        loop.refresh()
        entries.submitResult = Result.Failure(DataError.Network.Timeout)
        assertEquals(WriteResult.Queued, loop.submit(MINE, null, "key-1", NOON_ON_THE_DAY))
        outbox.queue.clear()
        entries.submitResult = Result.Failure(DataError.Network.Server(503))
        assertEquals(WriteResult.Queued, loop.submit(MINE, null, "key-2", NOON_ON_THE_DAY))

        outbox.queue.clear()
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        loop.refresh()
        entries.editResult = Result.Failure(DataError.Network.NoInternet)
        assertEquals(WriteResult.Failed, loop.edit("changed", "key-3"))
        assertTrue(outbox.queue.isEmpty(), "edits are online-only")
    }

    @Test
    fun aPhotoEntryIsNeverQueuedOfflineItFailsSoTheComposerKeepsTheWordsAndThePhoto() = runTest {
        loop.refresh()
        for (offline in listOf(DataError.Network.NoInternet, DataError.Network.Timeout, DataError.Network.Server(503))) {
            entries.submitResult = Result.Failure(offline)
            assertEquals(WriteResult.Failed, loop.submit(MINE, null, "key-1", NOON_ON_THE_DAY, imageMediaId = "m1"), "$offline")
        }
        assertTrue(outbox.queue.isEmpty(), "the outbox holds words only")
        assertEquals(0, outbox.retries)
        assertEquals("m1", entries.submitted.last().imageMediaId)
        assertEquals(TodayStatus.NotWritten, ready.status)
    }

    @Test
    fun aPhotoTheServerWillNotAttachIsSaidSoAndNothingIsQueued() = runTest {
        loop.refresh()
        entries.submitResult = Result.Failure(
            DataError.Network.Api(
                ApiProblem(422, ApiErrorCode.VALIDATION_FAILED, listOf(FieldError("imageMediaId", "MEDIA_NOT_ATTACHABLE", "cannot be attached"))),
            ),
        )
        assertEquals(WriteResult.PhotoRefused, loop.submit(MINE, null, "key-1", NOON_ON_THE_DAY, imageMediaId = "m1"))
        assertTrue(outbox.queue.isEmpty())
    }

    @Test
    fun aVoiceEntryIsNeverQueuedOfflineEither() = runTest {
        loop.refresh()
        for (offline in listOf(DataError.Network.NoInternet, DataError.Network.Timeout, DataError.Network.Server(503))) {
            entries.submitResult = Result.Failure(offline)
            assertEquals(WriteResult.Failed, loop.submit(MINE, null, "key-1", NOON_ON_THE_DAY, voiceMediaId = "v1"), "$offline")
        }
        assertTrue(outbox.queue.isEmpty(), "the outbox holds words only")
        assertEquals(0, outbox.retries)
        assertEquals("v1", entries.submitted.last().voiceMediaId)
    }

    @Test
    fun aVoiceNoteTheServerWillNotAttachIsSaidSoAndTheSameForAPhotoAlongsideIt() = runTest {
        loop.refresh()
        entries.submitResult = Result.Failure(
            DataError.Network.Api(
                ApiProblem(422, ApiErrorCode.VALIDATION_FAILED, listOf(FieldError("voiceMediaId", "MEDIA_NOT_ATTACHABLE", "cannot be attached"))),
            ),
        )
        assertEquals(WriteResult.VoiceRefused, loop.submit(MINE, null, "key-1", NOON_ON_THE_DAY, imageMediaId = "m1", voiceMediaId = "v1"))
        assertEquals("m1", entries.submitted.last().imageMediaId, "an entry carries both")
        assertTrue(outbox.queue.isEmpty())
    }

    @Test
    fun anEditSendsANewPhotoOrVoiceNoteByNameAndNoneWhenThereIsNone() = runTest {
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        loop.refresh()
        assertEquals(WriteResult.Saved, loop.edit("changed", "key-1"))
        assertEquals(WriteResult.Saved, loop.edit("changed", "key-2", voiceMediaId = "v2"))
        assertEquals(WriteResult.Saved, loop.edit("changed", "key-3", imageMediaId = "m2"))
        assertEquals(
            listOf(Pair(null, null), Pair(null, "v2"), Pair("m2", null)),
            entries.edited.map { it.imageMediaId to it.voiceMediaId },
            "absent keeps the entry's own",
        )
    }

    @Test
    fun anEditWithMediaIsNeverQueuedAndARefusedOneIsSaidSo() = runTest {
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        loop.refresh()
        entries.editResult = Result.Failure(DataError.Network.NoInternet)
        assertEquals(WriteResult.Failed, loop.edit("changed", "key-1", voiceMediaId = "v2"))
        assertTrue(outbox.queue.isEmpty(), "an edit needs a connection")

        entries.editResult = Result.Failure(
            DataError.Network.Api(
                ApiProblem(422, ApiErrorCode.VALIDATION_FAILED, listOf(FieldError("voiceMediaId", "MEDIA_NOT_ATTACHABLE", "cannot be attached"))),
            ),
        )
        assertEquals(WriteResult.VoiceRefused, loop.edit("changed", "key-2", voiceMediaId = "v2"))
        entries.editResult = Result.Failure(
            DataError.Network.Api(
                ApiProblem(422, ApiErrorCode.VALIDATION_FAILED, listOf(FieldError("imageMediaId", "MEDIA_NOT_ATTACHABLE", "cannot be attached"))),
            ),
        )
        assertEquals(WriteResult.PhotoRefused, loop.edit("changed", "key-3", imageMediaId = "m2"))
    }

    @Test
    fun aQueueThatCannotBeWrittenFailsTheSendSoTheComposerKeepsTheWords() = runTest {
        loop.refresh()
        outbox.failEnqueue = true
        entries.submitResult = Result.Failure(DataError.Network.NoInternet)
        assertEquals(WriteResult.Failed, loop.submit(MINE, null, "key-1", NOON_ON_THE_DAY))
        assertEquals(TodayStatus.NotWritten, ready.status)
    }

    @Test
    fun theServerWinsOverAQueuedEntryOnceItHoldsMine() = runTest {
        outbox.queue += queuedAt(NOON_ON_THE_DAY)
        loop.refresh()
        assertEquals(TodayStatus.Queued, ready.status)

        // Sent in the background: the day now holds my entry.
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        loop.refresh()
        assertEquals(TodayStatus.Waiting, ready.status)
        assertNull(ready.queuedDay)
    }

    @Test
    fun anEntryQueuedOnAnEarlierBondDaySaysThatDayHasClosed() = runTest {
        // Tapped at 23:30 in Lagos on the 13th; it is now noon on the 14th.
        outbox.queue += queuedAt(Instant.parse("2026-09-13T22:30:00Z"), dayDate = "2026-09-13")
        loop.refresh()
        assertEquals(QueuedDay.EarlierDayClosed(LocalDate(2026, 9, 13)), ready.queuedDay)
    }

    @Test
    fun anEntryQueuedMoreThan36HoursAgoCountsForTheDayItSends() = runTest {
        outbox.queue += queuedAt(Instant.parse("2026-09-12T22:00:00Z"), dayDate = "2026-09-12")
        loop.refresh()
        assertEquals(QueuedDay.DayItSends, ready.queuedDay)
    }

    @Test
    fun aRefusalShowsUntilWriteOpensAndAnAlreadyWrittenOneIsForgottenWhenItsDayHasGone() = runTest {
        outbox.refusals["bond-1"] = RefusedEntry("bond-1", OutboxRefusal.NotAccepted, "2026-09-14")
        loop.refresh()
        assertEquals(OutboxRefusal.NotAccepted, ready.refusal)
        loop.dismissRefusal()
        assertNull(ready.refusal)
        assertTrue(outbox.refusals.isEmpty())

        outbox.refusals["bond-1"] = RefusedEntry("bond-1", OutboxRefusal.AlreadyWritten, "2026-09-13")
        loop.refresh()
        assertNull(ready.refusal, "yesterday already held my entry: nothing to say today")
        assertTrue(outbox.refusals.isEmpty())
    }

    private fun queuedAt(at: Instant, dayDate: String = "2026-09-14") =
        QueuedEntry("bond-1", MINE, null, "key-q", at, dayDate)
}
