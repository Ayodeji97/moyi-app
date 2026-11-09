package com.moyi.app.data.repository

import com.moyi.app.api.models.BondDayResponse
import com.moyi.app.api.models.DeletedPartnerEntryResponse
import com.moyi.app.api.models.EntryImageResponse
import com.moyi.app.api.models.EntryResponse
import com.moyi.app.api.models.EntryVoiceResponse
import com.moyi.app.api.models.LockedEntryResponse
import com.moyi.app.api.models.MemberResponse
import com.moyi.app.api.models.PartnerEntryResponse
import com.moyi.app.api.models.RevealedPartnerEntryResponse
import com.moyi.app.api.models.SubmittedPartnerEntryResponse
import com.moyi.app.api.models.TodayPromptResponse
import com.moyi.app.api.models.TodayResponse
import com.moyi.app.api.models.TodayStreakResponse
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryImage
import com.moyi.app.domain.today.EntryMediaStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.EntryVoice
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
            val today = today(day(status))
            assertEquals(status.name, today.toToday()?.status?.name)
        }
        assertEquals(BondDayResponse.Status.entries.map { it.name }, BondDayStatus.entries.map { it.name })
    }

    @Test
    fun theDateIsTheIsoBondDate() {
        assertEquals("2026-09-14", today(day(BondDayResponse.Status.OPEN)).toToday()?.date)
    }

    // A missing bondDay, date or status can no longer be built: the contract makes them
    // required, so such a body fails to decode (TodayDecodeTest), before any mapper runs.

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
        val day = day(BondDayResponse.Status.PARTIAL)
        assertNull(today(day, myEntry = entry(EntryResponse.Status.SUBMITTED, null)).toToday())
        assertNull(today(day, partnerEntry = revealed(null)).toToday())
    }

    @Test
    fun everyPartnerShape() {
        assertEquals(PartnerEntry.Submitted("e1", "w"), submitted("w").toPartnerEntry())
        assertEquals(PartnerEntry.Revealed("e1", "w"), revealed("w").toPartnerEntry())
        assertEquals(PartnerEntry.Deleted("e1"), deleted().toPartnerEntry())
        assertEquals(PartnerEntry.Locked, LockedEntryResponse("m2").toPartnerEntry())
        // An entry read in full without its words is no entry.
        assertNull(submitted(null).toPartnerEntry())
        assertNull(revealed(null).toPartnerEntry())
    }

    @Test
    fun aPromptNeedsBothHalves() {
        val day = day(BondDayResponse.Status.OPEN)
        assertEquals(Prompt("p1", "t"), today(day, prompt = TodayPromptResponse("p1", "t")).toToday()?.prompt)
        assertNull(today(day).toToday()?.prompt)
        // Half a prompt can no longer be built: `id` and `text` are required, so it fails to decode.
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
    }

    @Test
    fun aPhotoIsMappedAsTheServerShowsItToTheReader() {
        val ready = EntryImageResponse(height = 960, id = "m1", sensitive = false, status = EntryImageResponse.Status.READY, url = URL, width = 1280)
        assertEquals(EntryImage("m1", EntryMediaStatus.Ready, URL, 1280, 960, false), ready.toEntryImage())
        // Sensitive only on a ready photo (the contract: false unless READY).
        assertTrue(ready.copy(sensitive = true).toEntryImage().sensitive)
        // Processing: never a URL, whatever was sent.
        val processing = ready.copy(status = EntryImageResponse.Status.PROCESSING, sensitive = true)
        assertEquals(EntryImage("m1", EntryMediaStatus.Processing, null, 1280, 960, false), processing.toEntryImage())
        // Not shared: the author still has the photo, unless it was blocked outright.
        assertEquals(EntryMediaStatus.NotShared, ready.copy(status = EntryImageResponse.Status.NOT_SHARED).toEntryImage().status)
        assertEquals(URL, ready.copy(status = EntryImageResponse.Status.NOT_SHARED).toEntryImage().url)
        assertNull(ready.copy(status = EntryImageResponse.Status.NOT_SHARED, url = null).toEntryImage().url)
        // A ready photo without its URL cannot be drawn: read as still on its way.
        assertEquals(EntryMediaStatus.Processing, ready.copy(url = null).toEntryImage().status)
    }

    @Test
    fun aVoiceNoteIsMappedAsTheServerPlaysItToTheReader() {
        val ready = EntryVoiceResponse(durationMs = 42_000, id = "v1", sensitive = false, status = EntryVoiceResponse.Status.READY, url = URL)
        assertEquals(EntryVoice("v1", EntryMediaStatus.Ready, URL, 42_000, false), ready.toEntryVoice())
        assertTrue(ready.copy(sensitive = true).toEntryVoice().sensitive)
        // Processing: never a URL or a length, whatever was sent, and never sensitive.
        val processing = ready.copy(status = EntryVoiceResponse.Status.PROCESSING, sensitive = true)
        assertEquals(EntryVoice("v1", EntryMediaStatus.Processing, null, null, false), processing.toEntryVoice())
        // Not shared: the author can still play it, unless it was blocked outright.
        val notShared = ready.copy(status = EntryVoiceResponse.Status.NOT_SHARED)
        assertEquals(EntryVoice("v1", EntryMediaStatus.NotShared, URL, 42_000, false), notShared.toEntryVoice())
        assertNull(notShared.copy(url = null, durationMs = null).toEntryVoice().url)
        // A ready voice note without its URL cannot be played: read as still on its way.
        assertEquals(EntryMediaStatus.Processing, ready.copy(url = null).toEntryVoice().status)
        // A length that is not a length is left for the player to learn.
        assertNull(ready.copy(durationMs = 0).toEntryVoice().durationMs)
    }

    @Test
    fun anEntrysVoiceNoteTravelsWithItAndATombstoneHasNone() {
        val voice = EntryVoiceResponse(durationMs = 42_000, id = "v1", sensitive = false, status = EntryVoiceResponse.Status.READY, url = URL)
        assertEquals("v1", entry(EntryResponse.Status.SUBMITTED, "w").copy(voice = voice).toMyEntry()?.voice?.id)
        assertNull(entry(EntryResponse.Status.DELETED, null).copy(voice = voice).toMyEntry()?.voice)
        val theirs = revealed("w").copy(voice = voice).toPartnerEntry()
        assertEquals("v1", assertIs<PartnerEntry.Revealed>(theirs).voice?.id)
        val solo = submitted("w").copy(voice = voice).toPartnerEntry()
        assertEquals("v1", assertIs<PartnerEntry.Submitted>(solo).voice?.id)
        // The voice note's URL never reaches a log.
        assertTrue("(redacted)" in voice.toEntryVoice().toString() && URL !in voice.toEntryVoice().toString())
    }

    @Test
    fun anEntrysPhotoTravelsWithItAndATombstoneHasNone() {
        val image = EntryImageResponse(height = 960, id = "m1", sensitive = false, status = EntryImageResponse.Status.READY, url = URL, width = 1280)
        assertEquals("m1", entry(EntryResponse.Status.SUBMITTED, "w").copy(image = image).toMyEntry()?.image?.id)
        assertNull(entry(EntryResponse.Status.DELETED, null).copy(image = image).toMyEntry()?.image)
        val theirs = revealed("w").copy(image = image).toPartnerEntry()
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

    private fun day(status: BondDayResponse.Status) = BondDayResponse(date = DATE, settlesAt = AT, status = status)

    private fun today(
        day: BondDayResponse,
        myEntry: EntryResponse? = null,
        partnerEntry: PartnerEntryResponse? = null,
        prompt: TodayPromptResponse? = null,
    ) = TodayResponse(
        bondDay = day,
        myEntry = myEntry,
        partnerEntry = partnerEntry,
        prompt = prompt,
        streak = TodayStreakResponse(current = 0, freezesAvailable = 0, longest = 0, strictMode = false),
    )

    private fun submitted(text: String?) = SubmittedPartnerEntryResponse(
        authorMemberId = "m2", bondId = "b1", createdAt = AT, date = DATE, favourited = false, id = "e1",
        image = null, intendedAt = AT, reaction = null, text = text, voice = null,
    )

    private fun revealed(text: String?) = RevealedPartnerEntryResponse(
        authorMemberId = "m2", bondId = "b1", createdAt = AT, date = DATE, favourited = false, id = "e1",
        image = null, intendedAt = AT, reaction = null, text = text, voice = null,
    )

    private fun deleted() = DeletedPartnerEntryResponse(
        authorMemberId = "m2", bondId = "b1", createdAt = AT, date = DATE, favourited = false, id = "e1",
        image = null, intendedAt = AT, reaction = null, text = null, voice = null,
    )

    private fun member(id: String, name: String, left: Boolean = false) = MemberResponse(
        displayName = name,
        id = id,
        joinedAt = AT,
        leftAt = if (left) Instant.parse("2026-09-01T00:00:00Z") else null,
        role = MemberResponse.Role.MEMBER,
    )

    private companion object {
        val DATE = LocalDate(2026, 9, 14)
        val AT = Instant.parse("2026-09-14T19:12:44Z")
        const val URL = "http://localhost:9000/moyi-media/display?X-Amz-Signature=abc"
    }
}
