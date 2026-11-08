package com.moyi.app.data.repository

import com.moyi.app.api.models.BondDayResponse
import com.moyi.app.api.models.EntryResponse
import com.moyi.app.api.models.ErasedEntryResponse
import com.moyi.app.api.models.LockedEntryResponse
import com.moyi.app.api.models.MemberResponse
import com.moyi.app.api.models.TodayPromptResponse
import com.moyi.app.api.models.TodayResponse
import com.moyi.app.api.wire.PartnerEntryWire
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Prompt
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** The wire-to-domain mapping, every enum value and every shape, without a network. */
class TodayMappersTest {
    @Test
    fun everyDayStatusTheContractDeclaresMapsToTheSameName() {
        for (status in BondDayResponse.Status.entries) {
            val today = TodayResponse(bondDay = BondDayResponse(DATE, status))
            assertEquals(status.name, today.toToday()?.status?.name)
        }
        assertEquals(BondDayResponse.Status.entries.map { it.name }, BondDayStatus.entries.map { it.name })
    }

    @Test
    fun theDateIsTheIsoBondDate() {
        assertEquals("2026-09-14", TodayResponse(bondDay = BondDayResponse(DATE, BondDayResponse.Status.OPEN)).toToday()?.date)
    }

    @Test
    fun aMissingBondDayOrItsDateOrStatusIsNoDay() {
        assertNull(TodayResponse().toToday())
        assertNull(TodayResponse(bondDay = BondDayResponse(date = DATE)).toToday())
        assertNull(TodayResponse(bondDay = BondDayResponse(status = BondDayResponse.Status.OPEN)).toToday())
    }

    @Test
    fun myEntryInEachStatus() {
        assertEquals(MyEntry("e1", "w", EntryStatus.SUBMITTED), entry(EntryResponse.Status.SUBMITTED, "w").toMyEntry())
        assertEquals(MyEntry("e1", "w", EntryStatus.REVEALED), entry(EntryResponse.Status.REVEALED, "w").toMyEntry())
        assertEquals(MyEntry("e1", null, EntryStatus.DELETED), entry(EntryResponse.Status.DELETED, null).toMyEntry())
        // A tombstone never carries words, even if a server sent some.
        assertEquals(MyEntry("e1", null, EntryStatus.DELETED), entry(EntryResponse.Status.DELETED, "w").toMyEntry())
        assertNull(entry(EntryResponse.Status.SUBMITTED, null).toMyEntry())
        assertNull(entry(EntryResponse.Status.REVEALED, null).toMyEntry())
    }

    @Test
    fun aMalformedEntryFailsTheWholeDay() {
        val day = BondDayResponse(DATE, BondDayResponse.Status.PARTIAL)
        assertNull(TodayResponse(bondDay = day, myEntry = entry(EntryResponse.Status.SUBMITTED, null)).toToday())
        assertNull(
            TodayResponse(bondDay = day, partnerEntry = PartnerEntryWire.Entry(entry(EntryResponse.Status.REVEALED, null))).toToday(),
        )
    }

    @Test
    fun everyPartnerShape() {
        assertEquals(PartnerEntry.Submitted("e1", "w"), PartnerEntryWire.Entry(entry(EntryResponse.Status.SUBMITTED, "w")).toPartnerEntry())
        assertEquals(PartnerEntry.Revealed("e1", "w"), PartnerEntryWire.Entry(entry(EntryResponse.Status.REVEALED, "w")).toPartnerEntry())
        assertEquals(PartnerEntry.Deleted("e1"), PartnerEntryWire.Entry(entry(EntryResponse.Status.DELETED, null)).toPartnerEntry())
        assertEquals(PartnerEntry.Locked, PartnerEntryWire.Locked(LockedEntryResponse("m2", LockedEntryResponse.Status.LOCKED)).toPartnerEntry())
        assertEquals(PartnerEntry.Removed, PartnerEntryWire.Erased(ErasedEntryResponse("m2", ErasedEntryResponse.Status.REMOVED)).toPartnerEntry())
    }

    @Test
    fun aPromptNeedsBothHalves() {
        val day = BondDayResponse(DATE, BondDayResponse.Status.OPEN)
        assertEquals(Prompt("p1", "t"), TodayResponse(bondDay = day, prompt = TodayPromptResponse("p1", "t")).toToday()?.prompt)
        assertNull(TodayResponse(bondDay = day, prompt = TodayPromptResponse(id = "p1")).toToday()?.prompt)
        assertNull(TodayResponse(bondDay = day, prompt = TodayPromptResponse(text = "t")).toToday()?.prompt)
    }

    @Test
    fun thePartnerIsTheOtherMemberAndOneStillHereComesFirst() {
        val me = member("m1", "Amara")
        val left = member("m3", "Kemi", left = true)
        val partner = member("m2", "Tunde")
        assertEquals("Tunde", partnerName(listOf(me, left, partner), "m1"))
        assertEquals("Kemi", partnerName(listOf(me, left), "m1"), "a member who left is still named")
        assertEquals("Amara", partnerName(listOf(me, partner), "m2"))
    }

    @Test
    fun noPartnerWhenNobodyElseIsListedOrTheCallerIsUnknown() {
        assertNull(partnerName(listOf(member("m1", "Amara")), "m1"))
        assertNull(partnerName(emptyList(), "m1"))
        assertNull(partnerName(null, "m1"))
        assertNull(partnerName(listOf(member("m1", "Amara"), member("m2", "Tunde")), null), "never name the caller as their own partner")
        assertNull(partnerName(listOf(member("m1", "Amara"), MemberResponse(id = "m2")), "m1"), "a member without a name names nobody")
    }

    private fun entry(status: EntryResponse.Status, text: String?) = EntryResponse(
        authorMemberId = "m1",
        bondId = "b1",
        createdAt = Instant.parse("2026-09-14T19:12:44Z"),
        date = DATE,
        favourited = false,
        id = "e1",
        intendedAt = Instant.parse("2026-09-14T19:12:44Z"),
        reaction = null,
        status = status,
        text = text,
    )

    private fun member(id: String, name: String, left: Boolean = false) = MemberResponse(
        displayName = name,
        id = id,
        leftAt = if (left) Instant.parse("2026-09-01T00:00:00Z") else null,
    )

    private companion object {
        val DATE = LocalDate(2026, 9, 14)
    }
}
