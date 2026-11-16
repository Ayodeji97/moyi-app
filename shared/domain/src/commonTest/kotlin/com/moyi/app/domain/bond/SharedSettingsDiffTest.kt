package com.moyi.app.domain.bond

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * [sharedSettingsNotices]: what changed in a bond's shared settings since this device last
 * saw them, told as one note per change. Never anything about who is in the bond (T-09).
 */
class SharedSettingsDiffTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val later = now + 3.days
    private val past = now - 1.days

    private val base = SharedSettingsSnapshot(
        bondId = "b1",
        status = BondStatus.ACTIVE,
        name = "Us",
        type = BondType.COUPLE,
        anchorTimezone = "Europe/London",
        revealTime = TimeOfDay(21, 0),
        strictMode = false,
        semanticSearchEnabled = true,
        deletionScheduled = false,
    )

    private fun notices(previous: SharedSettingsSnapshot?, current: SharedSettingsSnapshot) =
        sharedSettingsNotices(previous, current, now)

    // ----- No previous snapshot -----

    @Test
    fun theFirstLookNotesNothing() {
        assertEquals(emptyList(), notices(null, base.copy(name = "Anything")))
    }

    @Test
    fun nothingChangedNotesNothing() {
        assertEquals(emptyList(), notices(base, base))
    }

    // ----- The shared time zone (FR-027) -----

    private fun myZoneProposal(expiresAt: Instant = later) =
        PendingProposal(id = "p1", value = "Africa/Lagos", byMe = true, expiresAt = expiresAt)

    private fun theirZoneProposal(expiresAt: Instant = later) =
        PendingProposal(id = "p2", value = "Africa/Lagos", byMe = false, expiresAt = expiresAt)

    @Test
    fun myProposalAgreedNotesTheNewZone() {
        val before = base.copy(timezoneProposal = myZoneProposal())
        val after = base.copy(anchorTimezone = "Africa/Lagos")

        assertEquals(listOf(SharedSettingNotice.TimezoneAgreed("Africa/Lagos")), notices(before, after))
    }

    @Test
    fun myProposalGoneWithTheZoneUnchangedNotesThatItStays() {
        val before = base.copy(timezoneProposal = myZoneProposal())

        assertEquals(listOf(SharedSettingNotice.TimezoneKept("Europe/London")), notices(before, base))
    }

    @Test
    fun myProposalThatLapsedNotesNothing() {
        val before = base.copy(timezoneProposal = myZoneProposal(expiresAt = past))

        assertEquals(emptyList(), notices(before, base))
    }

    @Test
    fun theirProposalWithdrawnNotesIt() {
        val before = base.copy(timezoneProposal = theirZoneProposal())

        assertEquals(listOf(SharedSettingNotice.TimezoneWithdrawn("Africa/Lagos")), notices(before, base))
    }

    @Test
    fun theirProposalThatLapsedNotesNothing() {
        val before = base.copy(timezoneProposal = theirZoneProposal(expiresAt = past))

        assertEquals(emptyList(), notices(before, base))
    }

    @Test
    fun theirProposalAppliedIsMyOwnAgreementAndNotesNothing() {
        // I agreed, on another phone: nothing to tell me.
        val before = base.copy(timezoneProposal = theirZoneProposal())

        assertEquals(emptyList(), notices(before, base.copy(anchorTimezone = "Africa/Lagos")))
    }

    @Test
    fun aProposalStillWaitingNotesNothing() {
        val before = base.copy(timezoneProposal = myZoneProposal())

        assertEquals(emptyList(), notices(before, before))
    }

    @Test
    fun aProposalJustOpenedNotesNothingBecauseThePushSaysSo() {
        assertEquals(emptyList(), notices(base, base.copy(timezoneProposal = theirZoneProposal())))
    }

    // ----- Closing the box (FR-028) -----

    private fun request(byMe: Boolean, expiresAt: Instant = later) = PendingRequest(byMe = byMe, expiresAt = expiresAt)

    @Test
    fun myRequestToCloseAgreedNotesIt() {
        val before = base.copy(deletionRequest = request(byMe = true))
        val after = base.copy(status = BondStatus.PENDING_DELETION, deletionScheduled = true)

        assertEquals(listOf(SharedSettingNotice.DeletionAgreed), notices(before, after))
    }

    @Test
    fun myRequestToCloseGoneNotesThatTheBoxStaysOpen() {
        val before = base.copy(deletionRequest = request(byMe = true))

        assertEquals(listOf(SharedSettingNotice.DeletionKeptOpen), notices(before, base))
    }

    @Test
    fun theirRequestToCloseWithdrawnNotesIt() {
        val before = base.copy(deletionRequest = request(byMe = false))

        assertEquals(listOf(SharedSettingNotice.DeletionWithdrawn), notices(before, base))
    }

    @Test
    fun aLapsedRequestToCloseNotesNothing() {
        assertEquals(emptyList(), notices(base.copy(deletionRequest = request(byMe = true, expiresAt = past)), base))
        assertEquals(emptyList(), notices(base.copy(deletionRequest = request(byMe = false, expiresAt = past)), base))
    }

    @Test
    fun theirRequestAgreedIsMyOwnAndNotesNothing() {
        val before = base.copy(deletionRequest = request(byMe = false))
        val after = base.copy(status = BondStatus.PENDING_DELETION, deletionScheduled = true)

        assertEquals(emptyList(), notices(before, after))
    }

    @Test
    fun theCoolingOffCalledOffNotesIt() {
        val before = base.copy(status = BondStatus.PENDING_DELETION, deletionScheduled = true)

        assertEquals(listOf(SharedSettingNotice.DeletionCalledOff), notices(before, base))
    }

    // ----- Search by meaning -----

    private val searchOff = base.copy(semanticSearchEnabled = false)

    @Test
    fun myRequestForSearchAgreedNotesIt() {
        val before = searchOff.copy(semanticSearchRequest = request(byMe = true))

        assertEquals(listOf(SharedSettingNotice.SemanticSearchAgreed), notices(before, base))
    }

    @Test
    fun myRequestForSearchGoneNotesThatItStaysOff() {
        val before = searchOff.copy(semanticSearchRequest = request(byMe = true))

        assertEquals(listOf(SharedSettingNotice.SemanticSearchKeptOff), notices(before, searchOff))
    }

    @Test
    fun theirRequestForSearchWithdrawnNotesIt() {
        val before = searchOff.copy(semanticSearchRequest = request(byMe = false))

        assertEquals(listOf(SharedSettingNotice.SemanticSearchWithdrawn), notices(before, searchOff))
    }

    @Test
    fun aLapsedRequestForSearchNotesNothing() {
        assertEquals(emptyList(), notices(searchOff.copy(semanticSearchRequest = request(byMe = true, expiresAt = past)), searchOff))
    }

    @Test
    fun theirRequestForSearchAgreedIsMyOwnAndNotesNothing() {
        assertEquals(emptyList(), notices(searchOff.copy(semanticSearchRequest = request(byMe = false)), base))
    }

    @Test
    fun searchTurnedOffNotesIt() {
        assertEquals(listOf(SharedSettingNotice.SemanticSearchTurnedOff), notices(base, searchOff))
    }

    // ----- Changes one member makes alone -----

    @Test
    fun aNewNameNotesIt() {
        assertEquals(listOf(SharedSettingNotice.NameChanged("Tea and toast")), notices(base, base.copy(name = "Tea and toast")))
    }

    @Test
    fun aNewRevealTimeNotesIt() {
        assertEquals(listOf(SharedSettingNotice.RevealTimeChanged(TimeOfDay(20, 30))), notices(base, base.copy(revealTime = TimeOfDay(20, 30))))
    }

    @Test
    fun noRevealTimeAnyMoreNotesIt() {
        assertEquals(listOf(SharedSettingNotice.RevealTimeChanged(null)), notices(base, base.copy(revealTime = null)))
    }

    @Test
    fun restDaysTurnedOffNotesIt() {
        // Strict mode on is rest days off (states.md §8).
        assertEquals(listOf(SharedSettingNotice.RestDaysChanged(on = false)), notices(base, base.copy(strictMode = true)))
    }

    @Test
    fun aNewRelationshipTypeNotesIt() {
        assertEquals(listOf(SharedSettingNotice.TypeChanged(BondType.FRIENDS)), notices(base, base.copy(type = BondType.FRIENDS)))
    }

    @Test
    fun severalChangesAreOneNoteEachInAFixedOrder() {
        val after = base.copy(name = "New", revealTime = TimeOfDay(22, 0), strictMode = true)

        assertEquals(
            listOf(
                SharedSettingNotice.NameChanged("New"),
                SharedSettingNotice.RevealTimeChanged(TimeOfDay(22, 0)),
                SharedSettingNotice.RestDaysChanged(on = false),
            ),
            notices(base, after),
        )
    }

    // ----- T-09: leaving, blocking and who is in the bond never produce a note -----

    @Test
    fun theBondEndingNotesNothingEvenWhenItsProposalsVanish() {
        // Ending a bond cancels every open proposal (ADR-0030): read as "withdrawn", that would
        // tell the other person the bond ended because someone left or blocked.
        val before = base.copy(timezoneProposal = theirZoneProposal(), deletionRequest = request(byMe = true))
        val after = base.copy(status = BondStatus.ARCHIVED, name = "Changed")

        assertEquals(emptyList(), notices(before, after))
    }

    @Test
    fun anEndedBondNotesNothing() {
        val ended = base.copy(status = BondStatus.ARCHIVED)

        assertEquals(emptyList(), notices(ended, ended.copy(name = "Changed", strictMode = true)))
    }

    @Test
    fun someoneJoiningNotesNothing() {
        val waiting = base.copy(status = BondStatus.PENDING_MEMBER)

        assertEquals(emptyList(), notices(waiting, base.copy(name = "Changed")))
    }

    // ----- Changes made on this device (recordOwn's filter) -----

    @Test
    fun theSettingsThisDeviceChangedAreNotNoted() {
        val after = base.copy(name = "Mine", revealTime = TimeOfDay(20, 0))

        val noted = sharedSettingsNotices(base, after, now).withoutOwn(setOf(SharedSetting.NAME))

        assertEquals(listOf(SharedSettingNotice.RevealTimeChanged(TimeOfDay(20, 0))), noted)
    }
}
