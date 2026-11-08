package com.moyi.app.data.repository

import com.moyi.app.api.models.BondDayResponse
import com.moyi.app.api.models.EntryImageResponse
import com.moyi.app.api.models.EntryResponse
import com.moyi.app.api.models.LockedEntryResponse
import com.moyi.app.api.models.MemberResponse
import com.moyi.app.api.models.TodayPromptResponse
import com.moyi.app.api.models.TodayResponse
import com.moyi.app.api.wire.PartnerEntryWire
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryImage
import com.moyi.app.domain.today.EntryImageStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Prompt
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    @Test
    fun aPhotoIsMappedAsTheServerShowsItToTheReader() {
        val ready = EntryImageResponse(height = 960, id = "m1", sensitive = false, status = EntryImageResponse.Status.READY, url = URL, width = 1280)
        assertEquals(EntryImage("m1", EntryImageStatus.Ready, URL, 1280, 960, false), ready.toEntryImage())
        // Sensitive only on a ready photo (the contract: false unless READY).
        assertTrue(ready.copy(sensitive = true).toEntryImage().sensitive)
        // Processing: never a URL, whatever was sent.
        val processing = ready.copy(status = EntryImageResponse.Status.PROCESSING, sensitive = true)
        assertEquals(EntryImage("m1", EntryImageStatus.Processing, null, 1280, 960, false), processing.toEntryImage())
        // Not shared: the author still has the photo, unless it was blocked outright.
        assertEquals(EntryImageStatus.NotShared, ready.copy(status = EntryImageResponse.Status.NOT_SHARED).toEntryImage().status)
        assertEquals(URL, ready.copy(status = EntryImageResponse.Status.NOT_SHARED).toEntryImage().url)
        assertNull(ready.copy(status = EntryImageResponse.Status.NOT_SHARED, url = null).toEntryImage().url)
        // A ready photo without its URL cannot be drawn: read as still on its way.
        assertEquals(EntryImageStatus.Processing, ready.copy(url = null).toEntryImage().status)
    }

    @Test
    fun anEntrysPhotoTravelsWithItAndATombstoneHasNone() {
        val image = EntryImageResponse(height = 960, id = "m1", sensitive = false, status = EntryImageResponse.Status.READY, url = URL, width = 1280)
        assertEquals("m1", entry(EntryResponse.Status.SUBMITTED, "w").copy(image = image).toMyEntry()?.image?.id)
        assertNull(entry(EntryResponse.Status.DELETED, null).copy(image = image).toMyEntry()?.image)
        val theirs = PartnerEntryWire.Entry(entry(EntryResponse.Status.REVEALED, "w").copy(image = image)).toPartnerEntry()
        assertEquals("m1", assertIs<PartnerEntry.Revealed>(theirs).image?.id)
        // The image never reaches a log.
        assertTrue("(redacted)" in image.toEntryImage().toString() && URL !in image.toEntryImage().toString())
    }

    private fun entry(status: EntryResponse.Status, text: String?) = EntryResponse(
        authorMemberId = "m1",
        bondId = "b1",
        createdAt = Instant.parse("2026-09-14T19:12:44Z"),
        date = DATE,
        favourited = false,
        id = "e1",
        image = null,
        intendedAt = Instant.parse("2026-09-14T19:12:44Z"),
        reaction = null,
        status = status,
        text = text,
        voice = null,
    )

    private fun member(id: String, name: String, left: Boolean = false) = MemberResponse(
        displayName = name,
        id = id,
        leftAt = if (left) Instant.parse("2026-09-01T00:00:00Z") else null,
    )

    private companion object {
        val DATE = LocalDate(2026, 9, 14)
        const val URL = "http://localhost:9000/moyi-media/display?X-Amz-Signature=abc"
    }
}
