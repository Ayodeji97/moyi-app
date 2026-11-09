package com.moyi.app.data.repository

import com.moyi.app.api.models.DaysResponse
import com.moyi.app.api.models.DeletedPartnerEntryResponse
import com.moyi.app.api.models.LockedEntryResponse
import com.moyi.app.api.models.ProfileResponse
import com.moyi.app.api.models.RevealedPartnerEntryResponse
import com.moyi.app.api.models.SubmittedPartnerEntryResponse
import com.moyi.app.api.models.TodayResponse
import com.moyi.app.data.network.MoyiJson
import com.moyi.app.data.session.toUser
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.today.PartnerEntry
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The partner entry's `oneOf` decoded from JSON, through the app's own [MoyiJson], the
 * way the generated client reads a body. The samples are the local backend's real
 * answers (`GET /bonds/{id}/today`, `/days`, `/me`, 2026-11-09) with ids shortened and
 * the words replaced; `LOCKED` keeps the backend's field order (`status` last), and
 * `SUBMITTED` and `DELETED` are the same wide shape the backend renders for them.
 *
 * `status` picks the generated subclass (`@JsonClassDiscriminator("status")`) wherever
 * it sits in the object.
 */
class TodayDecodeTest {
    @Test
    fun aRevealedPartnerEntryDecodesToItsOwnSubclassAndMaps() {
        val today = MoyiJson.decodeFromString(TodayResponse.serializer(), todayWith(partner(status = "REVEALED", text = "\"Theirs\"")))
        val partner = assertIs<RevealedPartnerEntryResponse>(today.partnerEntry)
        assertEquals("e2", partner.id)
        val mapped = assertIs<PartnerEntry.Revealed>(today.toToday()?.partnerEntry)
        assertEquals("Theirs", mapped.text)
        assertEquals("v2", mapped.voice?.id)
    }

    @Test
    fun aLockedPartnerEntryCarriesOnlyItsAuthor() {
        val today = MoyiJson.decodeFromString(TodayResponse.serializer(), todayWith("""{"authorMemberId": "m2", "status": "LOCKED"}"""))
        assertEquals("m2", assertIs<LockedEntryResponse>(today.partnerEntry).authorMemberId)
        assertEquals(PartnerEntry.Locked, today.toToday()?.partnerEntry)
    }

    @Test
    fun aSubmittedAndADeletedPartnerEntryDecodeToTheirOwnSubclasses() {
        val submitted = MoyiJson.decodeFromString(TodayResponse.serializer(), todayWith(partner(status = "SUBMITTED", text = "\"Theirs\"")))
        assertIs<SubmittedPartnerEntryResponse>(submitted.partnerEntry)
        assertIs<PartnerEntry.Submitted>(submitted.toToday()?.partnerEntry)

        val deleted = MoyiJson.decodeFromString(TodayResponse.serializer(), todayWith(partner(status = "DELETED", text = "null", voice = "null")))
        assertIs<DeletedPartnerEntryResponse>(deleted.partnerEntry)
        assertEquals(PartnerEntry.Deleted("e2"), deleted.toToday()?.partnerEntry)
    }

    @Test
    fun noPartnerEntryIsNull() {
        val today = MoyiJson.decodeFromString(TodayResponse.serializer(), todayWith("null"))
        assertNull(today.partnerEntry)
        assertNull(today.toToday()?.partnerEntry)
    }

    @Test
    fun anUnknownStatusOrNoneIsABodyThatDidNotParse() {
        assertFailsWith<SerializationException> {
            MoyiJson.decodeFromString(TodayResponse.serializer(), todayWith("""{"authorMemberId": "m2", "status": "HIDDEN"}"""))
        }
        assertFailsWith<SerializationException> {
            MoyiJson.decodeFromString(TodayResponse.serializer(), todayWith("""{"authorMemberId": "m2"}"""))
        }
    }

    @Test
    fun aDayWithoutItsBondDayIsABodyThatDidNotParse() {
        // Before the contract made these required, the mapper caught this; now decoding does.
        assertFailsWith<SerializationException> {
            MoyiJson.decodeFromString(TodayResponse.serializer(), """{"myEntry": null, "partnerEntry": null, "prompt": null, "streak": $STREAK}""")
        }
    }

    @Test
    fun anArchivePageDecodesItsPartnerEntriesTheSameWay() {
        val page = MoyiJson.decodeFromString(
            DaysResponse.serializer(),
            """{"items": [{"date": "2026-11-09", "status": "REVEALED", "myEntry": ${mine()}, "partnerEntry": ${partner(status = "REVEALED", text = "\"Theirs\"")}, "partnerDisplayName": "Fin"}], "nextCursor": null}""",
        )
        assertIs<RevealedPartnerEntryResponse>(page.items.single().partnerEntry)
        assertIs<PartnerEntry.Revealed>(page.toArchivePage()?.days?.single()?.partnerEntry)
    }

    @Test
    fun theProfileFromMeIsTheUser() {
        val profile = MoyiJson.decodeFromString(
            ProfileResponse.serializer(),
            """
            {"id": "u1", "email": "eve@example.test", "displayName": "Eve", "locale": "en", "emailVerified": true, "status": "ACTIVE",
             "legalAccepted": {"terms": {"version": "2026-09-01", "acceptedAt": "2026-11-08T20:24:09.292316Z"},
                               "privacy": {"version": "2026-09-01", "acceptedAt": "2026-11-08T20:24:09.292316Z"}}}
            """.trimIndent(),
        )
        val user = profile.toUser()
        assertEquals("u1", user.id)
        assertEquals(UserStatus.ACTIVE, user.status)
    }

    @Test
    fun aPendingDeletionOnMeDecodesAndTheUserIsUnchanged() {
        // `deletionRequest` is required and nullable since backend #162: `null` when none
        // is pending, the dates while one is. Neither changes who the user is.
        for (deletionRequest in listOf(
            "null",
            """{"requestedAt": "2026-11-09T10:00:00Z", "cancellableUntil": "2026-11-23T10:00:00Z", "deleteBy": "2026-12-09T10:00:00Z"}""",
        )) {
            val profile = MoyiJson.decodeFromString(
                ProfileResponse.serializer(),
                """
                {"id": "u1", "email": "eve@example.test", "displayName": "Eve", "locale": "en", "emailVerified": true, "status": "ACTIVE",
                 "legalAccepted": {"terms": null, "privacy": null}, "deletionRequest": $deletionRequest}
                """.trimIndent(),
            )
            assertEquals(deletionRequest != "null", profile.deletionRequest != null)
            assertEquals("u1", profile.toUser().id)
        }
    }

    private fun todayWith(partnerEntry: String) =
        """{"bondDay": {"date": "2026-11-09", "status": "REVEALED", "settlesAt": "2026-11-10T12:00:00Z"}, "myEntry": ${mine()}, "partnerEntry": $partnerEntry, "streak": $STREAK, "prompt": {"id": "p1", "text": "A prompt"}}"""

    private fun mine() =
        """{"id": "e1", "bondId": "b1", "date": "2026-11-09", "authorMemberId": "m1", "text": "Mine", "status": "REVEALED", "createdAt": "2026-11-09T07:38:46.119642Z", "intendedAt": "2026-11-09T07:38:44.782Z", "favourited": false, "reaction": "SMILE", "image": null, "voice": null}"""

    private fun partner(status: String, text: String, voice: String = VOICE) =
        """{"id": "e2", "bondId": "b1", "date": "2026-11-09", "authorMemberId": "m2", "text": $text, "status": "$status", "createdAt": "2026-11-09T07:39:40.142594Z", "intendedAt": "2026-11-09T07:39:40.142594Z", "favourited": false, "reaction": null, "image": null, "voice": $voice}"""

    private companion object {
        const val STREAK = """{"current": 2, "longest": 2, "freezesAvailable": 0, "strictMode": false}"""
        const val VOICE = """{"id": "v2", "status": "READY", "url": "http://localhost:9000/moyi-media/v2", "durationMs": 5007, "sensitive": false}"""
    }
}
