package com.moyi.app.feature.today

import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.EntryPhotoLook
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.component.VoiceNoteLook
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.today.EntryImage
import com.moyi.app.domain.today.EntryMediaStatus
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.feature.today.archive.ArchiveDayAction
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.MediaRecheckDelays
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.holdsMediaOnItsWay
import com.moyi.app.feature.today.common.recheckWhileOnItsWay
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayViewModel
import com.moyi.app.core.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A partner's photo or voice note "on its way" turns into the photo while the screen is open:
 * the day is read again on a backoff while anything is on its way, and never otherwise.
 */
class MediaRecheckTest : MainDispatcherTest() {

    // ---- The schedule itself ----

    @Test
    fun readsAgainOnTheBackoffWhileSomethingIsOnItsWay() = runTest {
        val onItsWay = MutableStateFlow(true)
        val reads = mutableListOf<Long>()
        val job = launch { recheckWhileOnItsWay(onItsWay, reload = { reads += currentTime }) }
        advanceTimeBy(5.seconds + 1.milliseconds)
        assertEquals(listOf(5_000L), reads)
        advanceTimeBy(10.seconds)
        assertEquals(listOf(5_000L, 15_000L), reads)
        advanceTimeBy(20.seconds)
        advanceTimeBy(30.seconds)
        advanceTimeBy(60.seconds)
        assertEquals(listOf(5_000L, 15_000L, 35_000L, 65_000L, 125_000L), reads)
        job.cancel()
    }

    @Test
    fun readsNothingWhileNothingIsOnItsWay() = runTest {
        val onItsWay = MutableStateFlow(false)
        var reads = 0
        val job = launch { recheckWhileOnItsWay(onItsWay, reload = { reads++ }) }
        advanceTimeBy(10.minutes)
        assertEquals(0, reads)
        job.cancel()
    }

    @Test
    fun stopsOnceTheReadShowsItReady() = runTest {
        val onItsWay = MutableStateFlow(true)
        var reads = 0
        // The second read finds the photo cleared, as the loop's refresh would publish it.
        val job = launch { recheckWhileOnItsWay(onItsWay, reload = { if (++reads == 2) onItsWay.value = false }) }
        advanceTimeBy(5.minutes)
        assertEquals(2, reads)
        job.cancel()
    }

    @Test
    fun anotherLoadClearingItCutsTheWaitAndStartsTheScheduleOver() = runTest {
        val onItsWay = MutableStateFlow(true)
        val reads = mutableListOf<Long>()
        val job = launch { recheckWhileOnItsWay(onItsWay, reload = { reads += currentTime }) }
        advanceTimeBy(5.seconds + 1.milliseconds) // first read at 5 s; now waiting 10 s
        onItsWay.value = false // a foreground refresh showed the photo
        runCurrent()
        advanceTimeBy(1.minutes)
        assertEquals(listOf(5_000L), reads)
        val again = currentTime
        onItsWay.value = true // a new photo on its way: soon again, not in a minute
        advanceTimeBy(5.seconds + 1.milliseconds)
        assertEquals(listOf(5_000L, again + 5_000L), reads)
        job.cancel()
    }

    @Test
    fun stopsAfterTheLastWaitOfTheSchedule() = runTest {
        val onItsWay = MutableStateFlow(true)
        var reads = 0
        val job = launch { recheckWhileOnItsWay(onItsWay, reload = { reads++ }) }
        val total = MediaRecheckDelays.fold(Duration.ZERO) { sum, d -> sum + d }
        advanceTimeBy(total + 1.milliseconds)
        assertEquals(MediaRecheckDelays.size, reads)
        assertFalse(job.isActive, "a photo held back for review must not keep the phone asking all day")
        advanceTimeBy(1.hours)
        assertEquals(MediaRecheckDelays.size, reads)
    }

    @Test
    fun theScheduleIsGentle() {
        assertEquals(listOf(5.seconds, 10.seconds, 20.seconds, 30.seconds, 60.seconds), MediaRecheckDelays.take(5))
        assertTrue(MediaRecheckDelays.drop(4).all { it == 60.seconds })
        val total = MediaRecheckDelays.fold(Duration.ZERO) { sum, d -> sum + d }
        assertTrue(total in 29.minutes..31.minutes, "about half an hour, was $total")
    }

    // ---- Which days count ----

    @Test
    fun onlyMediaOnItsWayCounts() {
        val shown = EntryPhoto("p", "https://local.test/p", 10, 10, EntryPhotoLook.Shown)
        val onItsWay = EntryPhoto("p", null, null, null, EntryPhotoLook.OnItsWay)
        val voiceOnItsWay = VoiceNote("v", null, null, VoiceNoteLook.OnItsWay)
        assertFalse(dayView(TodayStatus.Revealed).holdsMediaOnItsWay())
        assertFalse(dayView(TodayStatus.Revealed).copy(partnerPhoto = shown).holdsMediaOnItsWay())
        assertTrue(dayView(TodayStatus.Revealed).copy(partnerPhoto = onItsWay).holdsMediaOnItsWay())
        assertTrue(dayView(TodayStatus.Revealed).copy(partnerVoice = voiceOnItsWay).holdsMediaOnItsWay())
        assertTrue(dayView(TodayStatus.Waiting).copy(myPhoto = onItsWay).holdsMediaOnItsWay())
    }

    // ---- Today ----

    private val loop = FakeDailyLoop()
    private val today by lazy { TodayViewModel(loop, RevealLedger { "me" }, InviteBonds(), FakeOutbox(), FakeNotificationAskMemory()) }

    private val photoOnItsWay = DailyLoopState.Ready(
        dayView(TodayStatus.Revealed).copy(partnerPhoto = EntryPhoto("p", null, null, null, EntryPhotoLook.OnItsWay)),
    )
    private val photoReady = DailyLoopState.Ready(
        dayView(TodayStatus.Revealed).copy(partnerPhoto = EntryPhoto("p", "https://local.test/p", 10, 10, EntryPhotoLook.Shown)),
    )

    @Test
    fun todayReadsTheDayAgainUntilThePhotoIsReady() = runTest {
        loop.onRefresh = photoOnItsWay
        today.onAction(TodayAction.OnForeground)
        assertEquals(1, loop.refreshes)
        advanceTimeBy(5.seconds + 1.milliseconds)
        assertEquals(2, loop.refreshes)
        loop.onRefresh = photoReady // the server has cleared it
        advanceTimeBy(10.seconds)
        assertEquals(3, loop.refreshes)
        assertEquals(photoReady, loop.state.value)
        advanceTimeBy(30.minutes)
        assertEquals(3, loop.refreshes, "nothing on its way: no more reads")
    }

    @Test
    fun todayStopsReadingWhenItStopsShowing() = runTest {
        loop.onRefresh = photoOnItsWay
        today.onAction(TodayAction.OnForeground)
        advanceTimeBy(5.seconds + 1.milliseconds)
        assertEquals(2, loop.refreshes)
        today.onAction(TodayAction.OnBackground)
        advanceTimeBy(30.minutes)
        assertEquals(2, loop.refreshes)
        // Back on screen: the foreground read, then the schedule from the start.
        today.onAction(TodayAction.OnForeground)
        assertEquals(3, loop.refreshes)
        advanceTimeBy(5.seconds + 1.milliseconds)
        assertEquals(4, loop.refreshes)
        today.onAction(TodayAction.OnBackground)
    }

    @Test
    fun aSecondForegroundDoesNotStartASecondSchedule() = runTest {
        loop.onRefresh = photoOnItsWay
        today.onAction(TodayAction.OnForeground)
        today.onAction(TodayAction.OnForeground)
        assertEquals(2, loop.refreshes)
        advanceTimeBy(5.seconds + 1.milliseconds)
        assertEquals(3, loop.refreshes, "one re-read, not one per foreground")
        today.onAction(TodayAction.OnBackground)
    }

    @Test
    fun theRetryTapStartsNoSchedule() = runTest {
        loop.onRefresh = photoOnItsWay
        today.onAction(TodayAction.OnRetryClick)
        advanceTimeBy(1.minutes)
        assertEquals(1, loop.refreshes)
    }

    // ---- The Archive's day ----

    private val repository = FakeArchiveRepository()
    private val archiveLoop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val cache = FakeArchiveCache()
    private val feed = ArchiveFeed(archiveLoop, repository, cache)

    private fun dayWithPhoto(status: EntryMediaStatus) = revealedDay("2026-09-02").copy(
        partnerEntry = PartnerEntry.Revealed(
            "theirs-2026-09-02",
            THEIRS,
            image = EntryImage("p", status, "https://local.test/p".takeIf { status == EntryMediaStatus.Ready }, 10, 10, sensitive = false),
        ),
    )

    @Test
    fun theArchiveDayReadsItselfAgainWhileShownUntilThePhotoIsReady() = runTest {
        val processing = dayWithPhoto(EntryMediaStatus.Processing)
        repository.pages[null] = ArchivePage(listOf(processing), null)
        cache.putPage("bond-1", ArchivePage(listOf(processing), null), after = null)
        val day = ArchiveDayViewModel("2026-09-02", feed, repository, FakeEntries(), archiveLoop)
        day.onAction(ArchiveDayAction.OnResumed)
        val first = repository.dayCalls.size
        advanceTimeBy(5.seconds + 1.milliseconds)
        assertEquals(first + 1, repository.dayCalls.size)
        repository.dayAnswer = Result.Success(dayWithPhoto(EntryMediaStatus.Ready))
        advanceTimeBy(10.seconds)
        assertEquals(first + 2, repository.dayCalls.size)
        assertEquals(EntryPhotoLook.Shown, day.state.value.day?.partnerPhoto?.look)
        advanceTimeBy(30.minutes)
        assertEquals(first + 2, repository.dayCalls.size)
    }

    @Test
    fun theArchiveDayStopsReadingWhenPaused() = runTest {
        val processing = dayWithPhoto(EntryMediaStatus.Processing)
        repository.pages[null] = ArchivePage(listOf(processing), null)
        cache.putPage("bond-1", ArchivePage(listOf(processing), null), after = null)
        val day = ArchiveDayViewModel("2026-09-02", feed, repository, FakeEntries(), archiveLoop)
        day.onAction(ArchiveDayAction.OnResumed)
        day.onAction(ArchiveDayAction.OnPaused)
        val before = repository.dayCalls.size
        advanceTimeBy(30.minutes)
        assertEquals(before, repository.dayCalls.size)
    }

}
