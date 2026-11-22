package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.Result
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.DraftKind
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.QueuedEntry
import com.moyi.app.domain.today.Reaction
import com.moyi.app.feature.today.archive.ArchiveAction
import com.moyi.app.feature.today.archive.ArchiveDayAction
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.archive.ArchiveViewModel
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.RepositoryDailyLoop
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.today.OwnEntryMenuItem
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayViewModel
import com.moyi.app.feature.today.today.ownEntryMenu
import com.moyi.app.feature.today.write.WriteAction
import com.moyi.app.feature.today.write.WriteEvent
import com.moyi.app.feature.today.write.WriteSubmit
import com.moyi.app.feature.today.write.WriteViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The paused bond (ADR-0066): `paused` on Today, and `409 BOND_PAUSED` on a send, an edit and
 * a reaction. Read-only for entries: nothing new is written, nothing already written is lost,
 * and a draft or a queued entry is never dropped or refused. The outbox's own handling of the
 * `409` is OutboxSenderTest (data layer).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PausedBondTest : MainDispatcherTest() {

    // ---- The daily loop, over fakes of the repositories ----

    private val bonds = FakeBonds()
    private val entries = FakeTodayEntries()
    private val cache = FakeTodayCache()
    private val outbox = FakeOutbox()
    private val loop = RepositoryDailyLoop(bonds, entries, cache, outbox, outbox, FixedClock(NOON_ON_THE_DAY), FakeArchiveCache())
    private val ready get() = assertIs<DailyLoopState.Ready>(loop.state.value).day

    private val paused = api(409, ApiErrorCode.BOND_PAUSED)

    @Test
    fun aPausedDayIsShownPausedAndItsRevealedEntryTakesNoReaction() = runTest {
        val revealed = today(
            BondDayStatus.REVEALED,
            my = MY_ENTRY.copy(status = EntryStatus.REVEALED),
            theirs = PartnerEntry.Revealed("entry-2", THEIRS, reaction = Reaction.HEART),
        )
        entries.todayResult = Result.Success(revealed.copy(paused = true, prompt = null))
        loop.refresh()

        assertTrue(ready.paused)
        assertEquals(TodayStatus.Revealed, ready.status, "both entries are still there to read")
        assertEquals(THEIRS, ready.partnerEntry)
        assertFalse(ready.canReact, "the server would answer 409 BOND_PAUSED")
        assertEquals(Reaction.HEART, ready.myReaction, "the reaction already left is still known")
        assertTrue(cache.stored!!.today.paused, "kept with the day, so a launch from the cache shows it paused")

        // It resumes: the next answer says so, and the set comes back.
        entries.todayResult = Result.Success(revealed)
        loop.refresh()
        assertFalse(ready.paused)
        assertTrue(ready.canReact)
    }

    @Test
    fun aSendIntoAPausedBondIsNeitherQueuedNorRefusedAndTodayIsReadAgain() = runTest {
        loop.refresh()
        entries.submitResult = Result.Failure(paused)
        entries.todayResult = Result.Success(today().copy(paused = true, prompt = null))
        val before = entries.todayCalls.size

        assertEquals(WriteResult.Paused, loop.submit(MINE, PROMPT.id, "key-1", NOON_ON_THE_DAY))

        assertTrue(outbox.queue.isEmpty(), "not queued: the composer keeps it, with its draft")
        assertTrue(outbox.refusals.isEmpty(), "and not a refusal")
        assertEquals(0, outbox.retries)
        assertEquals(before + 1, entries.todayCalls.size, "Today is read again, and shows the pause")
        assertTrue(ready.paused)
        assertEquals(TodayStatus.NotWritten, ready.status)
    }

    @Test
    fun aSendWithAPhotoOrAVoiceNoteIsPausedTooAndKeepsThem() = runTest {
        loop.refresh()
        entries.submitResult = Result.Failure(paused)
        assertEquals(WriteResult.Paused, loop.submit(MINE, null, "key-1", NOON_ON_THE_DAY, imageMediaId = "m1", voiceMediaId = "v1"))
        assertTrue(outbox.queue.isEmpty())
    }

    @Test
    fun anEditIntoAPausedBondIsPausedAndTheEntryStaysAsItWas() = runTest {
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY))
        loop.refresh()
        entries.editResult = Result.Failure(paused)
        entries.todayResult = Result.Success(today(BondDayStatus.PARTIAL, my = MY_ENTRY).copy(paused = true, prompt = null))

        assertEquals(WriteResult.Paused, loop.edit("changed", "key-1"))

        assertEquals(MINE, ready.myEntry, "the entry is what the server holds")
        assertTrue(ready.paused)
    }

    @Test
    fun aQueuedEntryInAPausedBondIsStillShownAsQueued() = runTest {
        outbox.queue += QueuedEntry("bond-1", MINE, null, "key-q", Instant.parse("2026-09-14T10:00:00Z"), "2026-09-14")
        entries.todayResult = Result.Success(today().copy(paused = true, prompt = null))
        loop.refresh()

        assertEquals(TodayStatus.Queued, ready.status, "held, never dropped")
        assertEquals(MINE, ready.myEntry)
        assertTrue(ready.paused, "so Today says it sends when the bond resumes")
    }

    @Test
    fun aReactionRefusedByAPauseIsPutBackAndTodayIsReadAgain() = runTest {
        val revealed = today(BondDayStatus.REVEALED, my = MY_ENTRY.copy(status = EntryStatus.REVEALED), theirs = PartnerEntry.Revealed("entry-2", THEIRS))
        entries.todayResult = Result.Success(revealed)
        loop.refresh()
        // Paused between the load and the tap.
        entries.reactResult = Result.Failure(paused)
        entries.todayResult = Result.Success(revealed.copy(paused = true, prompt = null))

        loop.react(Reaction.HEART)

        assertNull(ready.myReaction, "put back as it was")
        assertTrue(ready.paused)
        assertFalse(ready.canReact, "and the set goes")
    }

    // ---- Today ----

    private val dayLoop = FakeDailyLoop()
    private val todayViewModel by lazy {
        TodayViewModel(dayLoop, RevealLedger { "me" }, InviteBonds(), FakeOutbox(), FakeNotificationAskMemory(), FakeSettingsNotices())
    }

    private fun TestScope.showToday(day: com.moyi.app.feature.today.common.DayView) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { todayViewModel.state.collect {} }
        dayLoop.show(day)
    }

    @Test
    fun todayWhilePausedSaysSoAndOffersNoEdit() = runTest {
        showToday(dayView(TodayStatus.Waiting).copy(paused = true))
        val state = todayViewModel.state.value

        assertTrue(state.paused)
        assertEquals(listOf(OwnEntryMenuItem.Delete), state.ownEntryMenu(), "erasing your own entry still works; editing does not")
        todayViewModel.events.test {
            todayViewModel.onAction(TodayAction.OnEditClick)
            expectNoEvents()
        }
    }

    @Test
    fun todayWhilePausedKeepsTheReactionYouLeftButNotTheSet() = runTest {
        showToday(dayView(TodayStatus.Revealed).copy(paused = true, canReact = false, myReaction = Reaction.HEART))
        val state = todayViewModel.state.value

        assertFalse(state.canReact)
        assertEquals(Reaction.HEART, state.myReaction)
        todayViewModel.onAction(TodayAction.OnReactionClick(Reaction.MOVED))
        assertTrue(dayLoop.reactions.isEmpty())
    }

    // ---- Write ----

    @Test
    fun aPausedSendKeepsEverythingAndTheDraftSaysWhyAndTriesTheSameRequestAgain() = runTest {
        val writeLoop = FakeDailyLoop().apply {
            show(dayView(TodayStatus.NotWritten, myEntry = null))
            submitResult = WriteResult.Paused
        }
        val drafts = FakeDraftStore()
        var keys = 0
        val vm = WriteViewModel(false, writeLoop, drafts, newIdempotencyKey = { "key-${++keys}" }, clock = FixedClock(NOON_ON_THE_DAY))

        vm.events.test {
            vm.onAction(WriteAction.OnTextChange(MINE))
            vm.onAction(WriteAction.OnSubmit)
            expectNoEvents()
        }
        assertEquals(WriteSubmit.Paused, vm.state.value.submit)
        assertEquals(MINE, vm.state.value.text)
        assertEquals(MINE, drafts[DraftKind.NewEntry]?.text, "the draft stays for when the bond resumes")

        // Sent again unchanged: the server took nothing, and the same request is accepted once it resumes.
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(listOf("key-1", "key-1"), writeLoop.submits.map { it.key })

        // Typing clears the line, as it clears Save failed.
        vm.onAction(WriteAction.OnTextChange("$MINE Again."))
        assertEquals(WriteSubmit.Idle, vm.state.value.submit)
    }

    @Test
    fun aPausedEditKeepsTheChangedWordsAndDoesNotClose() = runTest {
        val writeLoop = FakeDailyLoop().apply {
            show(dayView(TodayStatus.Waiting, myEntry = MINE))
            editResult = WriteResult.Paused
        }
        val vm = WriteViewModel(true, writeLoop, FakeDraftStore(), newIdempotencyKey = { "key" }, clock = FixedClock(NOON_ON_THE_DAY))
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("changed"))
            vm.onAction(WriteAction.OnSubmit)
            expectNoEvents()
        }
        assertEquals(WriteSubmit.Paused, vm.state.value.submit)
        assertEquals("changed", vm.state.value.text)
    }

    // ---- The Archive ----

    @Test
    fun theArchiveOffersNoReactionSetWhileTheBondIsPaused() = runTest {
        val archiveLoop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed).copy(paused = true)))
        val repository = FakeArchiveRepository()
        val archiveCache = FakeArchiveCache()
        val page = ArchivePage(listOf(revealedDay("2026-09-02")), null)
        repository.pages[null] = page
        archiveCache.putPage("bond-1", page, after = null)
        val archiveEntries = FakeEntries()
        val view = ArchiveDayViewModel("2026-09-02", ArchiveFeed(archiveLoop, repository, archiveCache, FakeRememberRepository()), repository, archiveEntries, archiveLoop)

        assertEquals(MINE, view.state.value.day?.myEntry, "the day is still there to read")
        assertEquals(false, view.state.value.day?.canReact)
        view.onAction(ArchiveDayAction.OnReactionClick(Reaction.HEART))
        assertTrue(archiveEntries.reactions.isEmpty())
    }

    @Test
    fun anEmptyArchiveOffersNoWriteWhileTheBondIsPaused() = runTest {
        val archiveLoop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.NotWritten).copy(paused = true)))
        val repository = FakeArchiveRepository().apply { pages[null] = ArchivePage(emptyList(), null) }
        val list = ArchiveViewModel(ArchiveFeed(archiveLoop, repository, FakeArchiveCache(), FakeRememberRepository()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { list.state.collect {} }

        assertTrue(list.state.value.isEmpty)
        assertFalse(list.state.value.canWrite)
        list.events.test {
            list.onAction(ArchiveAction.OnWriteClick)
            expectNoEvents()
        }
    }

    @Test
    fun aSendRefusedForAnyOtherReasonIsStillSaveFailedNotPaused() = runTest {
        loop.refresh()
        entries.submitResult = Result.Failure(api(409, ApiErrorCode.BOND_ARCHIVED))
        assertEquals(WriteResult.Failed, loop.submit(MINE, null, "key-1", NOON_ON_THE_DAY))
    }
}
