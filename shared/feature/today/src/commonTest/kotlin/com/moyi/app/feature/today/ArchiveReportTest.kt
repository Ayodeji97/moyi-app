package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.feature.today.archive.ArchiveDayAction
import com.moyi.app.feature.today.archive.ArchiveDayEvent
import com.moyi.app.feature.today.archive.ArchiveDayViewModel
import com.moyi.app.feature.today.archive.ArchiveFeed
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.TodayStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The day view's Report (`26` §3.2, FR-100): the partner's entry is offered only when the day
 * shows its words, never mine; and the day is read again once the report flow is left.
 */
class ArchiveReportTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop(DailyLoopState.Ready(dayView(TodayStatus.Revealed)))
    private val repository = FakeArchiveRepository()
    private val entries = FakeEntries()
    private val cache = FakeArchiveCache()
    private val feed = ArchiveFeed(loop, repository, cache)

    private fun view(date: String) = ArchiveDayViewModel(date, feed, repository, entries, loop)

    private suspend fun seed(vararg days: com.moyi.app.domain.archive.ArchiveDay) {
        val page = ArchivePage(days.toList(), null)
        repository.pages[null] = page
        cache.putPage("bond-1", page, after = null)
    }

    @Test
    fun aRevealedDayOffersThePartnersEntryNeverMine() = runTest {
        seed(revealedDay("2026-09-02"))
        val day = view("2026-09-02")
        assertTrue(day.state.value.canReport)
        day.events.test {
            day.onAction(ArchiveDayAction.OnReportClick)
            assertEquals(ArchiveDayEvent.NavigateToReport("bond-1", "theirs-2026-09-02", PARTNER), awaitItem())
        }
    }

    @Test
    fun aDayWithoutTheirWordsReportsTheMemberOnly() = runTest {
        seed(revealedDay("2026-09-02").copy(partnerEntry = PartnerEntry.Deleted("theirs-2026-09-02")))
        val day = view("2026-09-02")
        day.events.test {
            day.onAction(ArchiveDayAction.OnReportClick)
            assertEquals(ArchiveDayEvent.NavigateToReport("bond-1", null, PARTNER), awaitItem())
        }
    }

    @Test
    fun theDayIsReadAgainOnlyAfterAReportWasOpened() = runTest {
        seed(revealedDay("2026-09-02"))
        val day = view("2026-09-02")
        val before = repository.dayCalls.size
        day.onAction(ArchiveDayAction.OnResumed)
        assertEquals(before, repository.dayCalls.size, "an ordinary resume reads nothing")
        day.events.test {
            day.onAction(ArchiveDayAction.OnReportClick)
            awaitItem()
        }
        day.onAction(ArchiveDayAction.OnResumed)
        assertEquals(before + 1, repository.dayCalls.size)
        day.onAction(ArchiveDayAction.OnResumed)
        assertEquals(before + 1, repository.dayCalls.size, "once per report")
    }
}
