package com.moyi.app.feature.today

import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.toDayView
import com.moyi.app.feature.today.common.todayStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The table in [TodayStatus]'s comment, one row at a time (states.md §3, §5a). */
class TodayMapperTest {
    private val mine = MyEntry(id = "entry-1", text = MINE, status = EntryStatus.SUBMITTED)
    private val mineDeleted = MyEntry(id = "entry-1", text = null, status = EntryStatus.DELETED)
    private val theirsLocked = PartnerEntry.Locked
    private val theirsOpen = PartnerEntry.Revealed(id = "entry-2", text = THEIRS)

    private fun status(day: BondDayStatus, my: MyEntry?, theirs: PartnerEntry? = null, pendingMember: Boolean = false) =
        todayStatus(day, my, theirs, pendingMember)

    @Test
    fun beforeIWriteTheLayoutIsNotWrittenWhetherOrNotThePartnerHas() {
        for (day in listOf(BondDayStatus.OPEN, BondDayStatus.PARTIAL, BondDayStatus.PENDING_REVEAL, BondDayStatus.SUSPENDED)) {
            assertEquals(TodayStatus.NotWritten, status(day, my = null))
            assertEquals(TodayStatus.NotWritten, status(day, my = null, theirs = theirsLocked), "$day with theirs locked")
            assertEquals(TodayStatus.NotWritten, status(day, my = mineDeleted), "$day after deleting mine")
        }
    }

    @Test
    fun onceIHaveWrittenItIsWaitingAndPendingRevealIsNeverRevealed() {
        assertEquals(TodayStatus.Waiting, status(BondDayStatus.PARTIAL, mine))
        assertEquals(TodayStatus.Waiting, status(BondDayStatus.PENDING_REVEAL, mine, theirsLocked))
    }

    @Test
    fun aBondStillWaitingForItsSecondMemberShowsPartnerNotJoined() {
        assertEquals(TodayStatus.PartnerNotJoined, status(BondDayStatus.SUSPENDED, mine, pendingMember = true))
        assertEquals(TodayStatus.PartnerNotJoined, status(BondDayStatus.PARTIAL, mine, pendingMember = true))
    }

    @Test
    fun revealedAndTheTombstoneOfMine() {
        assertEquals(TodayStatus.Revealed, status(BondDayStatus.REVEALED, mine, theirsOpen))
        assertEquals(TodayStatus.Tombstone, status(BondDayStatus.REVEALED, mineDeleted, theirsOpen))
        assertEquals(TodayStatus.NotWritten, status(BondDayStatus.REVEALED, my = null))
    }

    @Test
    fun aSoloDayShowsWhoseLoneEntryItWas() {
        assertEquals(TodayStatus.SoloYouWrote, status(BondDayStatus.SOLO, mine))
        assertEquals(TodayStatus.SoloTheyWrote, status(BondDayStatus.SOLO, null, PartnerEntry.Submitted("entry-2", THEIRS)))
        assertEquals(TodayStatus.NotWritten, status(BondDayStatus.SOLO, null, theirsLocked))
    }

    @Test
    fun emptyAndFrozenStandInWithTheLayoutThatShowsLeast() {
        for (day in listOf(BondDayStatus.EMPTY, BondDayStatus.FROZEN)) {
            assertEquals(TodayStatus.SoloYouWrote, status(day, mine))
            assertEquals(TodayStatus.NotWritten, status(day, null, theirsOpen))
        }
    }

    // ---- toDayView ----

    private val bond = Bond(
        id = "bond-1",
        name = "Amara and Tunde",
        type = BondType.COUPLE,
        status = BondStatus.ACTIVE,
        invite = null,
        partnerName = PARTNER,
    )

    private fun today(status: BondDayStatus, my: MyEntry?, theirs: PartnerEntry?, date: String = "2026-09-14") =
        Today(date = date, status = status, myEntry = my, partnerEntry = theirs, prompt = PROMPT)

    @Test
    fun aDayNeverCarriesThePartnersWordsIntoALayoutThatHidesThem() {
        // Even if a server sent the words early, a waiting day holds nothing of them.
        val view = assertNotNull(toDayView(bond, today(BondDayStatus.PENDING_REVEAL, mine, theirsOpen)))
        assertEquals(TodayStatus.Waiting, view.status)
        assertNull(view.partnerEntry)

        val open = assertNotNull(toDayView(bond, today(BondDayStatus.REVEALED, mine, theirsOpen)))
        assertEquals(THEIRS, open.partnerEntry)
        assertEquals(MINE, open.myEntry)
        assertEquals(DAY, open.date)
    }

    @Test
    fun myDeletedEntryKeepsItsIdButNotItsWords() {
        val view = assertNotNull(toDayView(bond, today(BondDayStatus.REVEALED, mineDeleted, theirsOpen)))
        assertEquals("entry-1", view.myEntryId)
        assertNull(view.myEntry)
    }

    @Test
    fun aDayThatCannotBeShownHonestlyIsNotShownAtAll() {
        assertNull(toDayView(bond, today(BondDayStatus.OPEN, null, null, date = "14/09/2026")), "a date that is not ISO")
        // Waiting names the partner ("You'll see Tunde's…"); a bond that names nobody cannot fill it.
        assertNull(toDayView(bond.copy(partnerName = null), today(BondDayStatus.PARTIAL, mine, null)))
        // Not written names nobody, so it can still be shown.
        assertNotNull(toDayView(bond.copy(partnerName = null), today(BondDayStatus.OPEN, null, null)))
    }
}
