package com.moyi.app.data.repository

import com.moyi.app.data.testing.inMemoryDatabase
import com.moyi.app.domain.archive.ARCHIVE_CACHE_MAX_DAYS
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.DatePeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SqlArchiveCache] over a real (in-memory) SQLite: the run comes back as it went, the
 * server wins over every date a page covers, the run never has a gap, it is bounded, and
 * it is only ever its own person's.
 */
class SqlArchiveCacheTest {
    private val ada = User("ada-id", "ada@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE)
    private val ben = ada.copy(id = "ben-id", email = "ben@example.test", displayName = "Ben")
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(ada))
    private val database = inMemoryDatabase()
    private val cache = SqlArchiveCache(database, session)

    private fun day(date: String, theirs: PartnerEntry? = PartnerEntry.Revealed("t-$date", "You listened."), marked: Set<String> = emptySet()) = ArchiveDay(
        date = date,
        status = BondDayStatus.REVEALED,
        myEntry = MyEntry("m-$date", "You made the tea.", EntryStatus.REVEALED),
        partnerEntry = theirs,
        markedByMe = marked,
    )

    /** [count] days, newest first, ending the day before [before]. */
    private fun days(before: String, count: Int): List<ArchiveDay> {
        val start = LocalDate.parse(before)
        return (1..count).map { day(start.minus(DatePeriod(days = it)).toString()) }
    }

    @Test
    fun everyShapeOfADayComesBackAsItWent() = runTest {
        val shapes = listOf(
            day("2026-09-07", PartnerEntry.Revealed("t1", "You listened."), marked = setOf("m-2026-09-07", "t1")),
            day("2026-09-06", PartnerEntry.Submitted("t2", "You called.")),
            day("2026-09-05", PartnerEntry.Deleted("t3")),
            day("2026-09-04", PartnerEntry.Locked),
            day("2026-09-03", null),
            day("2026-09-02").copy(myEntry = MyEntry("m-2026-09-02", null, EntryStatus.DELETED), status = BondDayStatus.EMPTY),
            day("2026-09-01").copy(myEntry = null),
        )
        cache.putPage("bond-1", ArchivePage(shapes, "c1"), after = null)

        assertEquals(ArchivePage(shapes, "c1"), cache.read("bond-1"))
        assertEquals(shapes[0], cache.day("bond-1", "2026-09-07"))
    }

    @Test
    fun theRunIsOnlyItsOwnPersonsAndBonds() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07")), null), after = null)

        assertNull(cache.read("bond-2"), "another bond's run is not this one")
        session.value = SessionState.SignedIn(ben)
        assertNull(cache.read("bond-1"), "another person never reads it, even on the same bond")
        assertNull(cache.day("bond-1", "2026-09-07"))

        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-06")), null), after = null)
        session.value = SessionState.SignedIn(ada)
        assertNull(cache.read("bond-1"), "writing Ben's run removed Ada's: one run in the table")
        assertEquals(1L, database.rowCount("archive_day"))
    }

    @Test
    fun signedOutNothingIsReadOrWritten() = runTest {
        session.value = SessionState.SignedOut
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07")), null), after = null)
        assertEquals(0L, database.rowCount("archive_day"))
        assertNull(cache.read("bond-1"))
    }

    @Test
    fun theNewestPageReplacesEveryCachedDayInTheDatesItCoversAndKeepsTheOlderRun() = runTest {
        // Two pages cached: 09-07..09-05 (cursor c1), then 09-04..09-02 (cursor c2).
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-06"), day("2026-09-05")), "c1"), after = null)
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-04"), day("2026-09-03"), day("2026-09-02")), "c2"), after = "2026-09-05")

        // On the server since: a new day, 09-07 now my tombstone, a favourite on 09-06 set on the
        // other phone, and 09-05's partner entry erased before it was seen: their words are gone.
        val tombstone = day("2026-09-07").copy(myEntry = MyEntry("m-2026-09-07", null, EntryStatus.DELETED))
        val marked = day("2026-09-06", marked = setOf("t-2026-09-06"))
        val erased = day("2026-09-05", theirs = null)
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-08"), tombstone, marked, erased), "c1"), after = null)

        val run = cache.read("bond-1")!!
        assertEquals(listOf(day("2026-09-08"), tombstone, marked, erased) + listOf(day("2026-09-04"), day("2026-09-03"), day("2026-09-02")), run.days)
        assertNull(run.days.first { it.date == "2026-09-05" }.partnerEntry)
        assertEquals("c2", run.nextCursor, "the older run is kept, so the cursor after it still is")
    }

    @Test
    fun aDayTheNewPageNoLongerListsIsGone() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-06"), day("2026-09-05")), "c1"), after = null)

        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-05")), "c1"), after = null)

        assertEquals(listOf("2026-09-07", "2026-09-05"), cache.read("bond-1")!!.days.map { it.date })
    }

    @Test
    fun aNewestPageThatDoesNotReachTheRunStartsANewOneRatherThanLeaveAGap() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-02"), day("2026-09-01")), "c1"), after = null)

        // More than a page of days came since: the days between 09-02 and 09-08 are unknown.
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-10"), day("2026-09-09"), day("2026-09-08")), "c9"), after = null)

        val run = cache.read("bond-1")!!
        assertEquals(listOf("2026-09-10", "2026-09-09", "2026-09-08"), run.days.map { it.date })
        assertEquals("c9", run.nextCursor)
    }

    @Test
    fun aLastNewestPageIsTheWholeArchive() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-06")), "c1"), after = null)
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-05")), null), after = "2026-09-06")

        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07")), null), after = null)

        assertEquals(ArchivePage(listOf(day("2026-09-07")), null), cache.read("bond-1"))
    }

    @Test
    fun anOlderPageAskedAgainReplacesItsDatesAndOnlyThose() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-06")), "c1"), after = null)
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-05"), day("2026-09-04")), "c2"), after = "2026-09-06")
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-03"), day("2026-09-02")), "c3"), after = "2026-09-04")

        // The middle page again: 09-04 is gone on the server, so the page of two reaches 09-03.
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-05", marked = setOf("t-2026-09-05")), day("2026-09-03")), "c2b"), after = "2026-09-06")

        val run = cache.read("bond-1")!!
        assertEquals(listOf("2026-09-07", "2026-09-06", "2026-09-05", "2026-09-03", "2026-09-02"), run.days.map { it.date })
        assertEquals(setOf("t-2026-09-05"), run.days[2].markedByMe)
        assertEquals("c3", run.nextCursor)
    }

    @Test
    fun anOlderPageThatDoesNotMeetTheRunIsNotKept() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-06")), "c1"), after = null)

        cache.putPage("bond-1", ArchivePage(listOf(day("2026-08-01")), "c8"), after = "2026-08-02")

        assertEquals(ArchivePage(listOf(day("2026-09-07"), day("2026-09-06")), "c1"), cache.read("bond-1"))
    }

    @Test
    fun anEmptyLastPageMarksTheEndOfTheRun() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-06")), "c1"), after = null)

        cache.putPage("bond-1", ArchivePage(emptyList(), null), after = "2026-09-06")

        assertNull(cache.read("bond-1")!!.nextCursor, "nothing is left to ask for")
    }

    @Test
    fun pastTheCapTheOldestPagesGoAndTheCursorStillContinuesTheRun() = runTest {
        // Twelve pages of twenty: 240 days, newest first.
        var after: String? = null
        var before = "2026-09-08"
        repeat(12) { index ->
            val page = days(before, 20)
            cache.putPage("bond-1", ArchivePage(page, "c$index"), after)
            after = page.last().date
            before = page.last().date
        }

        val run = cache.read("bond-1")!!
        assertEquals(ARCHIVE_CACHE_MAX_DAYS, run.days.size, "whole pages are dropped: ten of twenty")
        assertEquals("2026-09-07", run.days.first().date, "the newest are kept")
        assertEquals("c9", run.nextCursor, "the cursor after the tenth page: the eleventh is asked for next")
        assertTrue(run.days.zipWithNext().all { (newer, older) -> newer.date > older.date })
    }

    @Test
    fun anUpdateReplacesOnlyADayAlreadyCached() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-06")), "c1"), after = null)
        val marked = day("2026-09-06", marked = setOf("m-2026-09-06"))

        cache.update("bond-1", listOf(marked, day("2026-01-01")))

        assertEquals(ArchivePage(listOf(day("2026-09-07"), marked), "c1"), cache.read("bond-1"))
    }

    @Test
    fun aConfirmedMarkAndARemovedDayReachTheCache() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-06")), "c1"), after = null)

        cache.setMark("bond-1", "2026-09-07", "t-2026-09-07", marked = true)
        cache.setMark("bond-1", "2026-09-07", "m-2026-09-07", marked = true)
        cache.setMark("bond-1", "2026-09-07", "m-2026-09-07", marked = false)
        cache.remove("bond-1", "2026-09-06")

        assertEquals(ArchivePage(listOf(day("2026-09-07", marked = setOf("t-2026-09-07"))), "c1"), cache.read("bond-1"))
    }

    @Test
    fun theSessionWipeEmptiesTheArchive() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07")), null), after = null)

        database.wipe()

        assertNull(cache.read("bond-1"))
        assertEquals(0L, database.rowCount("archive_day"))
    }

    @Test
    fun anEmptyNewestPageWithNoCursorIsKeptAsAKnownEmptyArchive() = runTest {
        assertNull(cache.read("bond-1"), "before any answer: nothing cached, not empty")

        cache.putPage("bond-1", ArchivePage(emptyList(), null), after = null)

        assertEquals(ArchivePage(emptyList(), null), cache.read("bond-1"))
        assertNull(cache.read("bond-2"), "only for the bond it was answered for")
        session.value = SessionState.SignedIn(ben)
        assertNull(cache.read("bond-1"), "and only for its own person")
    }

    @Test
    fun anEmptyNewestPageWithACursorIsNotAnEmptyArchive() = runTest {
        cache.putPage("bond-1", ArchivePage(emptyList(), "c1"), after = null)

        assertNull(cache.read("bond-1"))
    }

    @Test
    fun theNextPageWithDaysClearsTheEmptyArchive() = runTest {
        cache.putPage("bond-1", ArchivePage(emptyList(), null), after = null)

        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07")), null), after = null)

        assertEquals(ArchivePage(listOf(day("2026-09-07")), null), cache.read("bond-1"))
        assertEquals(0L, database.rowCount("archive_empty"))
    }

    @Test
    fun anEmptyAnswerReplacesACachedRunAndAnotherBondsAnswerClearsIt() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07")), null), after = null)

        cache.putPage("bond-1", ArchivePage(emptyList(), null), after = null)
        assertEquals(ArchivePage(emptyList(), null), cache.read("bond-1"), "the server wins: the day was deleted")
        assertEquals(0L, database.rowCount("archive_day"))

        cache.putPage("bond-2", ArchivePage(listOf(day("2026-09-07")), null), after = null)
        assertNull(cache.read("bond-1"), "one answer in the tables, ever")
    }

    @Test
    fun theSessionWipeForgetsAnEmptyArchive() = runTest {
        cache.putPage("bond-1", ArchivePage(emptyList(), null), after = null)

        database.wipe()

        assertNull(cache.read("bond-1"))
        assertEquals(0L, database.rowCount("archive_empty"))
    }

    @Test
    fun aRowThisBuildCannotReadIsNoCacheAtAll() = runTest {
        cache.putPage("bond-1", ArchivePage(listOf(day("2026-09-07"), day("2026-09-06")), null), after = null)
        database.io { archiveDayQueries.upsert(day("2026-09-05").toRow("ada-id", "bond-1", null).copy(day_status = "SOMETHING_NEW")) }

        assertNull(cache.read("bond-1"))
    }
}
