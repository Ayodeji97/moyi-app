package com.moyi.app.data.repository

import com.moyi.app.data.testing.inMemoryDatabase
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.EntryImage
import com.moyi.app.domain.today.EntryMediaStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.EntryVoice
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Reaction
import com.moyi.app.domain.today.Prompt
import com.moyi.app.domain.today.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** [SqlTodayCache] over a real (in-memory) SQLite: what goes in comes back, for its own person only. */
class SqlTodayCacheTest {
    private val ada = User("ada-id", "ada@example.test", "Ada", emailVerified = true, status = UserStatus.ACTIVE)
    private val ben = ada.copy(id = "ben-id", email = "ben@example.test", displayName = "Ben")
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(ada))
    private val database = inMemoryDatabase()
    private val cache = SqlTodayCache(database, session)

    private val bond = Bond(
        id = "bond-1",
        name = "Amara and Tunde",
        type = BondType.COUPLE,
        status = BondStatus.ACTIVE,
        invite = null,
        partnerName = "Tunde",
        anchorTimezone = "Africa/Lagos",
    )

    private fun day(partner: PartnerEntry?, mine: MyEntry? = MyEntry("e1", "You made the tea.", EntryStatus.REVEALED)) = CachedDay(
        bond = bond,
        today = Today(
            date = "2026-09-14",
            status = BondDayStatus.REVEALED,
            myEntry = mine,
            partnerEntry = partner,
            prompt = Prompt("p1", "What did Tunde do today that you noticed?"),
        ),
    )

    @Test
    fun everyShapeOfTheDayComesBackAsItWent() = runTest {
        val shapes = listOf(
            PartnerEntry.Revealed("e2", "You listened."),
            PartnerEntry.Submitted("e2", "You listened."),
            PartnerEntry.Deleted("e2"),
            PartnerEntry.Locked,
            null,
        )
        for (partner in shapes) {
            cache.write(day(partner))
            assertEquals(day(partner), cache.read(), "partner entry $partner")
        }
        val tombstone = day(PartnerEntry.Revealed("e2", "You listened."), mine = MyEntry("e1", null, EntryStatus.DELETED))
        cache.write(tombstone)
        assertEquals(tombstone, cache.read())
        val unwritten = CachedDay(bond.copy(partnerName = null, anchorTimezone = null), day(null, mine = null).today.copy(prompt = null))
        cache.write(unwritten)
        assertEquals(unwritten, cache.read())
    }

    @Test
    fun theInviteIsNeverKept() = runTest {
        cache.write(day(null).copy(bond = bond.copy(invite = Invite("i1", "K7Q2PD", "https://getmoyi.app/i/K7Q2PD"))))
        assertNull(cache.read()?.bond?.invite)
    }

    @Test
    fun aDayIsReadOnlyByThePersonItWasWrittenFor() = runTest {
        cache.write(day(PartnerEntry.Revealed("e2", "You listened.")))

        session.value = SessionState.SignedIn(ben)
        assertNull(cache.read(), "Ben must never be shown Ada's day")

        session.value = SessionState.SignedOut
        assertNull(cache.read())
    }

    @Test
    fun nobodySignedInWritesNothing() = runTest {
        session.value = SessionState.Unknown
        cache.write(day(null))
        assertEquals(0L, database.rowCount("cached_day"))
    }

    @Test
    fun aNewWriteReplacesTheRowWhoeverItBelongedTo() = runTest {
        cache.write(day(null))
        session.value = SessionState.SignedIn(ben)
        cache.write(day(PartnerEntry.Locked))
        assertEquals(1L, database.rowCount("cached_day"))
        session.value = SessionState.SignedIn(ada)
        assertNull(cache.read())
    }

    @Test
    fun clearForgetsTheDay() = runTest {
        cache.write(day(null))
        cache.clear()
        assertNull(cache.read())
    }

    @Test
    fun aRowThisBuildCannotReadIsNoCacheNotAGuess() = runTest {
        database.io { cachedDayQueries.insert(day(null).toRow(ada.id).copy(day_status = "SOMETHING_NEW")) }
        assertNull(cache.read())
        database.io {
            cachedDayQueries.deleteAll()
            cachedDayQueries.insert(day(PartnerEntry.Locked).toRow(ada.id).copy(partner_entry_shape = "REVEALED"))
        }
        assertNull(cache.read(), "a revealed shape with no words is not a shape")
    }

    @Test
    fun theReactionOnEachEntryComesBackAsItWent() = runTest {
        val reacted = day(
            partner = PartnerEntry.Revealed("e2", "You listened.", reaction = Reaction.ME_TOO),
            mine = MyEntry("e1", "You made the tea.", EntryStatus.REVEALED, reaction = Reaction.SMILE),
        )
        cache.write(reacted)
        assertEquals(reacted, cache.read())
    }

    @Test
    fun aReactionNameThisBuildDoesNotKnowIsNoCacheNotAGuess() = runTest {
        cache.write(day(PartnerEntry.Revealed("e2", "You listened.")))
        database.io { cachedDayQueries.deleteAll() }
        database.io {
            cachedDayQueries.insert(day(PartnerEntry.Revealed("e2", "You listened.")).toRow("ada-id").copy(partner_entry_reaction = "WAVE"))
        }
        assertNull(cache.read())
    }

    @Test
    fun eachEntrysMediaComesBackWithItsShapeAndNeverItsUrl() = runTest {
        val photo = EntryImage("m1", EntryMediaStatus.Ready, "https://storage.test/m1?sig=secret", 1080, 1920, sensitive = false)
        val voice = EntryVoice("v1", EntryMediaStatus.Ready, "https://storage.test/v1.ogg?sig=secret", 12_000, sensitive = true)
        val theirPhoto = EntryImage("m2", EntryMediaStatus.NotShared, "https://storage.test/m2?sig=secret", 1600, 900, sensitive = true)
        val theirVoice = EntryVoice("v2", EntryMediaStatus.Processing, null, null, sensitive = false)
        val withMedia = day(
            partner = PartnerEntry.Revealed("e2", "You listened.", image = theirPhoto, voice = theirVoice),
            mine = MyEntry("e1", "You made the tea.", EntryStatus.REVEALED, image = photo, voice = voice),
        )

        cache.write(withMedia)

        // The size, the length, the status and the cover come back; the signed URL does not.
        val expected = day(
            partner = PartnerEntry.Revealed("e2", "You listened.", image = theirPhoto.copy(url = null), voice = theirVoice),
            mine = MyEntry("e1", "You made the tea.", EntryStatus.REVEALED, image = photo.copy(url = null), voice = voice.copy(url = null)),
        )
        assertEquals(expected, cache.read())
        val row = database.io { cachedDayQueries.forUser(ada.id).executeAsOne() }
        assertFalse(row.toString().contains("sig=secret"), "no URL is written to the device")

        // A solo day's partner entry (SUBMITTED) keeps its media the same way.
        val solo = day(PartnerEntry.Submitted("e2", "You listened.", image = theirPhoto.copy(url = null)), mine = null)
        cache.write(solo)
        assertEquals(solo, cache.read())
    }

    @Test
    fun aMediaStatusThisBuildDoesNotKnowIsNoCacheNotAGuess() = runTest {
        val photo = EntryImage("m1", EntryMediaStatus.Ready, null, 1080, 1920, sensitive = false)
        val row = day(null, mine = MyEntry("e1", "You made the tea.", EntryStatus.REVEALED, image = photo)).toRow(ada.id)
        database.io { cachedDayQueries.insert(row.copy(my_photo_status = "QUARANTINED")) }
        assertNull(cache.read())
    }
}
