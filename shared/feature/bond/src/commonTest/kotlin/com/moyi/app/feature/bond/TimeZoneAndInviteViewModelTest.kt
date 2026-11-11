package com.moyi.app.feature.bond

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.PendingTimezoneChange
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.settings_error_conflict
import com.moyi.app.feature.bond.resources.settings_error_rate_limited
import com.moyi.app.feature.bond.resources.settings_invite_error_full
import com.moyi.app.feature.bond.resources.settings_tz_error_too_soon
import com.moyi.app.feature.bond.resources.settings_tz_error_too_soon_from
import com.moyi.app.feature.bond.resources.settings_tz_error_unsupported
import com.moyi.app.feature.bond.settings.BondSettingsAction
import com.moyi.app.feature.bond.settings.BondSettingsEvent
import com.moyi.app.feature.bond.settings.BondSettingsViewModel
import com.moyi.app.feature.bond.settings.SettingsError
import com.moyi.app.feature.bond.settings.SettingsField
import com.moyi.app.feature.bond.settings.TimeZoneStep
import com.moyi.app.feature.bond.settings.dayAndMonth
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shared time zone (FR-027, ADR-0030: propose, proposed, confirm, the 30-day rule)
 * and the invite (states.md §2: share, copy, cancel, create, the 409s) on Bond settings.
 */
class TimeZoneAndInviteViewModelTest : MainDispatcherTest() {
    private val repo = FakeBondSettingsRepository()
    private val bonds = FakeBondRepository(repo)
    private var zones: Set<String> = setOf("Europe/London", "Africa/Lagos", "UTC", "Etc/GMT+1", "America/Argentina/Buenos_Aires")
    private val viewModel by lazy { BondSettingsViewModel("b1", repo, FakeAuthRepository(), bonds, FakeOutbox(), FakeOutbox(), zoneIds = { zones }, clock = fixedClock) }
    private val fixedClock = object : kotlin.time.Clock {
        // 23:30 UTC on 10 November is 00:30 on 11 November in Lagos (UTC+1).
        override fun now() = kotlin.time.Instant.parse("2026-11-10T23:30:00Z")
    }

    private val theirProposal = PendingTimezoneChange("p1", "Europe/London", proposedByMe = false)
    private val myProposal = PendingTimezoneChange("p1", "Europe/London", proposedByMe = true)

    private fun error(field: SettingsField, message: StringResource) = SettingsError(field, UiText.Resource(message))

    private val state get() = viewModel.state.value

    /** Opens Propose and, if [zone] is given, picks it, taking the two navigation events. */
    private suspend fun choose(zone: String?) = viewModel.events.test {
        viewModel.onAction(BondSettingsAction.OnTimeZoneClick)
        assertEquals(BondSettingsEvent.OpenTimeZone, awaitItem())
        if (zone != null) {
            viewModel.onAction(BondSettingsAction.OnZonePicked(zone))
            assertEquals(BondSettingsEvent.ZonePicked, awaitItem())
        }
    }

    // ----- propose -----

    @Test
    fun theRowOpensProposeOnTheBondsOwnZone() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnTimeZoneClick)
            assertEquals(BondSettingsEvent.OpenTimeZone, awaitItem())
        }
        assertEquals(TimeZoneStep.Propose, state.timeZoneStep)
        assertEquals("Africa/Lagos", state.zoneDraft)
    }

    @Test
    fun thePickerListsOnlyRegionZonesByCityAndSearchesThem() = runTest {
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnZoneFieldClick)
            assertEquals(BondSettingsEvent.OpenZonePicker, awaitItem())
        }
        // UTC and Etc/ are refused by the backend (a region zone only), so they are not offered.
        assertEquals(listOf("America/Argentina/Buenos_Aires", "Africa/Lagos", "Europe/London"), state.zones)

        viewModel.onAction(BondSettingsAction.OnZoneQueryChange("lon"))
        assertEquals(listOf("Europe/London"), state.visibleZones)
        viewModel.onAction(BondSettingsAction.OnZoneQueryChange("buenos"))
        assertEquals(listOf("America/Argentina/Buenos_Aires"), state.visibleZones)
    }

    @Test
    fun noZonesFromThePlatformIsTheFieldsErrorAndOpeningItAgainRetries() = runTest {
        zones = emptySet()
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnZoneFieldClick)
            expectNoEvents()
            assertTrue(state.zonesFailed)

            zones = setOf("Europe/London")
            viewModel.onAction(BondSettingsAction.OnZoneFieldClick)
            assertEquals(BondSettingsEvent.OpenZonePicker, awaitItem())
        }
        assertFalse(state.zonesFailed)
    }

    @Test
    fun pickingACityReturnsToProposeWithIt() = runTest {
        choose("Europe/London")
        assertEquals("Europe/London", state.zoneDraft)
    }

    @Test
    fun proposingWaitsForTheOtherMemberAndShowsProposed() = runTest {
        choose("Europe/London")
        repo.gate = CompletableDeferred()

        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnProposeClick)
            assertEquals(SettingsField.TimeZone, state.saving, "the action says Proposing while it is in flight")
            repo.gate!!.complete(Unit)
            expectNoEvents()
        }

        assertEquals(listOf("Europe/London"), repo.proposals)
        assertEquals(TimeZoneStep.Proposed, state.timeZoneStep)
        assertEquals("Africa/Lagos", state.settings?.anchorTimezone, "nothing moves until the other member agrees")
        assertEquals("\"2\"", state.settings?.version, "the 200's ETag is what the next settings change sends")
        assertNull(state.saving)
    }

    @Test
    fun proposingTheZoneItAlreadyIsSendsNothing() = runTest {
        choose(null)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnProposeClick)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertTrue(repo.proposals.isEmpty())
    }

    @Test
    fun onABondNobodyHasJoinedTheZoneChangesAtOnceAndTheRootShowsIt() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER)
        choose("Europe/London")
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnProposeClick)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals("Europe/London", state.settings?.anchorTimezone)
    }

    @Test
    fun aSecondChangeInsideThirtyDaysIsRefusedPlainlyAndNotRetried() = runTest {
        repo.proposeAnswers += apiError(409, ApiErrorCode.TIMEZONE_CHANGE_TOO_SOON)
        choose("Europe/London")

        viewModel.onAction(BondSettingsAction.OnProposeClick)

        assertEquals(error(SettingsField.TimeZone, Res.string.settings_tz_error_too_soon), state.error)
        assertEquals(1, repo.proposals.size, "a month is not a rate limit: no retry")
        assertEquals(TimeZoneStep.Propose, state.timeZoneStep)
        assertEquals("Europe/London", state.zoneDraft, "the choice is kept")
        assertNull(state.saving)
    }

    @Test
    fun aFutureAllowedFromIsSaidUpFrontAndTheChangeCannotStart() = runTest {
        repo.server = settingsV1.copy(timezoneChangeAllowedFrom = "2026-12-10")
        choose("Europe/London")

        assertEquals(LocalDate(2026, 12, 10), state.zoneChangeFrom)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnZoneFieldClick)
            viewModel.onAction(BondSettingsAction.OnProposeClick)
            expectNoEvents()
        }
        assertTrue(repo.proposals.isEmpty(), "no request that is sure to be refused")
        assertTrue(state.zones.isEmpty(), "the picker never opened")
        assertNull(state.saving)
    }

    @Test
    fun noAllowedFromMeansTheChangeIsOpen() = runTest {
        choose("Europe/London")

        assertNull(state.zoneChangeFrom)
        viewModel.onAction(BondSettingsAction.OnProposeClick)
        assertEquals(1, repo.proposals.size)
    }

    @Test
    fun anAllowedFromThatHasPassedOrIsTodayInTheBondsZoneIsOpen() = runTest {
        // The clock is 00:30 on 11 November in Lagos (but still 10 November in UTC).
        repo.server = settingsV1.copy(timezoneChangeAllowedFrom = "2026-11-11")
        choose("Europe/London")
        assertNull(state.zoneChangeFrom, "from that day's start it can change")
        viewModel.onAction(BondSettingsAction.OnProposeClick)
        assertEquals(1, repo.proposals.size)
    }

    @Test
    fun aStaleAllowedFromInThePastBehavesAsToday() = runTest {
        repo.server = settingsV1.copy(timezoneChangeAllowedFrom = "2026-10-01")
        choose("Europe/London")
        assertNull(state.zoneChangeFrom)
    }

    @Test
    fun theDayBeforeAllowedFromInTheBondsZoneStillBlocks() = runTest {
        // 11 Nov in Lagos; allowed from 12 Nov.
        repo.server = settingsV1.copy(timezoneChangeAllowedFrom = "2026-11-12")
        choose("Europe/London")
        assertEquals(LocalDate(2026, 11, 12), state.zoneChangeFrom)
    }

    @Test
    fun aMalformedAllowedFromDoesNotBlock() = runTest {
        repo.server = settingsV1.copy(timezoneChangeAllowedFrom = "soon")
        choose("Europe/London")
        assertNull(state.zoneChangeFrom)
    }

    @Test
    fun aProposalAlreadyOpenReloadsAndShowsTheirsToAnswer() = runTest {
        choose("Asia/Tokyo")
        repo.proposeAnswers += apiError(409, ApiErrorCode.PROPOSAL_PENDING)
        repo.server = settingsV1.copy(pendingTimezone = theirProposal, version = "\"2\"")

        viewModel.onAction(BondSettingsAction.OnProposeClick)

        assertEquals(TimeZoneStep.Confirm, state.timeZoneStep)
        assertEquals(error(SettingsField.TimeZone, Res.string.settings_error_conflict), state.error)
    }

    @Test
    fun aZoneTheBackendRefusesSaysSoOnTheScreen() = runTest {
        repo.proposeAnswers += apiError(422, ApiErrorCode.VALIDATION_FAILED)
        choose("Europe/London")

        viewModel.onAction(BondSettingsAction.OnProposeClick)

        assertEquals(error(SettingsField.TimeZone, Res.string.settings_tz_error_unsupported), state.error)
    }

    @Test
    fun anEndedBondEndsSettingsFromPropose() = runTest {
        repo.proposeAnswers += apiError(409, ApiErrorCode.BOND_ARCHIVED)
        choose("Europe/London")
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnProposeClick)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
    }

    // ----- proposed: the proposer's side -----

    @Test
    fun theProposerCanCancelButNeverAgree() = runTest {
        repo.server = settingsV1.copy(pendingTimezone = myProposal)
        assertEquals(TimeZoneStep.Proposed, state.timeZoneStep)

        viewModel.onAction(BondSettingsAction.OnAgreeClick)
        assertTrue(repo.confirmations.isEmpty(), "BR-6: one person clicking twice is not two people agreeing")

        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnWithdrawProposalClick)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals(1, repo.cancels)
        assertNull(state.settings?.pendingTimezone)
        assertEquals(TimeZoneStep.Propose, state.timeZoneStep)
    }

    @Test
    fun cancellingWhatIsNoLongerWaitingIsDoneNotEnded() = runTest {
        repo.server = settingsV1.copy(pendingTimezone = myProposal)
        viewModel.state.value
        repo.cancelAnswers += apiError(404, ApiErrorCode.NOT_FOUND)
        repo.server = settingsV1
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnWithdrawProposalClick)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertNull(state.settings?.pendingTimezone)
    }

    // ----- confirm: the other member's side -----

    @Test
    fun theOtherMemberAgreesAndTheZoneMoves() = runTest {
        repo.server = settingsV1.copy(pendingTimezone = theirProposal)
        assertEquals(TimeZoneStep.Confirm, state.timeZoneStep)

        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnAgreeClick)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals(listOf("p1"), repo.confirmations)
        assertEquals("Europe/London", state.settings?.anchorTimezone)
        assertNull(state.settings?.pendingTimezone)
    }

    @Test
    fun theOtherMemberDeclinesWithTheSameDelete() = runTest {
        repo.server = settingsV1.copy(pendingTimezone = theirProposal)
        viewModel.state.value
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnWithdrawProposalClick)
            assertEquals(BondSettingsEvent.Saved, awaitItem())
        }
        assertEquals(1, repo.cancels)
        assertEquals("Africa/Lagos", state.settings?.anchorTimezone)
    }

    @Test
    fun agreeingToAProposalThatLapsedReloadsAndSaysSoInsteadOfEnding() = runTest {
        repo.server = settingsV1.copy(pendingTimezone = theirProposal)
        viewModel.state.value
        repo.server = settingsV1 // lapsed, or cancelled from the other phone

        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnAgreeClick)
            expectNoEvents()
        }

        assertEquals(TimeZoneStep.Propose, state.timeZoneStep)
        assertEquals(error(SettingsField.TimeZone, Res.string.settings_error_conflict), state.error)
    }

    @Test
    fun theRefusalNamesTheDayItCanChangeAgain() = runTest {
        repo.proposeAnswers += apiError(409, ApiErrorCode.TIMEZONE_CHANGE_TOO_SOON, allowedFrom = "2026-12-12")
        choose("Europe/London")

        viewModel.onAction(BondSettingsAction.OnProposeClick)

        val day = dayAndMonth(LocalDate(2026, 12, 12))
        assertEquals(
            SettingsError(SettingsField.TimeZone, UiText.Resource(Res.string.settings_tz_error_too_soon_from, listOf(day))),
            state.error,
        )
        assertFalse(day.contains("2026-12-12"), "the day is written for a person, not as the raw ISO date")
        assertEquals(1, repo.proposals.size, "still never retried")
        assertNull(state.saving)
    }

    @Test
    fun aMalformedAllowedFromFallsBackToTheUndatedRefusal() = runTest {
        // The parser only passes real dates; this guards the ViewModel on its own.
        repo.proposeAnswers += apiError(409, ApiErrorCode.TIMEZONE_CHANGE_TOO_SOON, allowedFrom = "soon")
        choose("Europe/London")

        viewModel.onAction(BondSettingsAction.OnProposeClick)

        assertEquals(error(SettingsField.TimeZone, Res.string.settings_tz_error_too_soon), state.error)
    }

    @Test
    fun theDatedRefusalAlsoComesAtConfirmation() = runTest {
        repo.server = settingsV1.copy(pendingTimezone = theirProposal)
        repo.confirmAnswers += apiError(409, ApiErrorCode.TIMEZONE_CHANGE_TOO_SOON, allowedFrom = "2027-01-03")

        viewModel.onAction(BondSettingsAction.OnAgreeClick)

        assertEquals(
            SettingsError(
                SettingsField.TimeZone,
                UiText.Resource(Res.string.settings_tz_error_too_soon_from, listOf(dayAndMonth(LocalDate(2027, 1, 3)))),
            ),
            state.error,
        )
        assertEquals(TimeZoneStep.Confirm, state.timeZoneStep)
    }

    @Test
    fun theThirtyDayRuleIsCheckedAgainAtConfirmation() = runTest {
        repo.server = settingsV1.copy(pendingTimezone = theirProposal)
        repo.confirmAnswers += apiError(409, ApiErrorCode.TIMEZONE_CHANGE_TOO_SOON)

        viewModel.onAction(BondSettingsAction.OnAgreeClick)

        assertEquals(error(SettingsField.TimeZone, Res.string.settings_tz_error_too_soon), state.error)
        assertEquals(TimeZoneStep.Confirm, state.timeZoneStep)
        assertNull(state.saving)
    }

    // ----- the invite -----

    @Test
    fun theInviteRowOpensTheInviteAndShareCarriesItsLinkAndCode() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER, invite = liveInvite)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnInviteClick)
            assertEquals(BondSettingsEvent.OpenInvite, awaitItem())
            viewModel.onAction(BondSettingsAction.OnShareInviteClick)
            assertEquals(BondSettingsEvent.ShareInvite(liveInvite.link, liveInvite.code), awaitItem())
        }
        assertEquals(0, bonds.created, "sharing never issues: that would revoke the code being shared")
    }

    @Test
    fun copyingTheCodeShowsCopied() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER, invite = liveInvite)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnCopyInviteClick)
            assertEquals(BondSettingsEvent.CopyInviteCode("K7Q2PD"), awaitItem())
        }
        assertTrue(state.inviteCopied)
    }

    @Test
    fun cancellingTheInviteRevokesItAndShowsRevoked() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER, invite = liveInvite)
        viewModel.onAction(BondSettingsAction.OnCopyInviteClick)

        viewModel.onAction(BondSettingsAction.OnCancelInviteClick)

        assertEquals(listOf("i1"), bonds.revoked)
        assertNull(state.settings?.invite)
        assertFalse(state.inviteCopied)
        assertNull(state.saving)
    }

    @Test
    fun cancellingAnInviteAlreadyDeadIsCancelled() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER, invite = liveInvite)
        viewModel.state.value
        bonds.revokeAnswers += apiError(404, ApiErrorCode.NOT_FOUND)
        repo.server = repo.server.copy(invite = null)
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnCancelInviteClick)
            expectNoEvents()
        }
        assertNull(state.settings?.invite)
        assertNull(state.error)
    }

    @Test
    fun creatingANewInviteShowsItsCode() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER)

        viewModel.onAction(BondSettingsAction.OnCreateInviteClick)

        assertEquals(1, bonds.created)
        assertEquals(newInvite, state.settings?.invite)
    }

    @Test
    fun aBondThatFilledMeanwhileSaysItNeedsNoInvite() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER)
        viewModel.state.value
        bonds.createAnswers += apiError(409, ApiErrorCode.BOND_FULL)
        repo.server = settingsV1 // ACTIVE now: they joined

        viewModel.onAction(BondSettingsAction.OnCreateInviteClick)

        assertEquals(error(SettingsField.Invite, Res.string.settings_invite_error_full), state.error)
        assertEquals(BondStatus.ACTIVE, state.settings?.status, "reloaded, so the root drops the Invite row")
        assertNull(state.saving)
    }

    @Test
    fun anEndedBondEndsSettingsFromTheInvite() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER)
        bonds.createAnswers += apiError(409, ApiErrorCode.BOND_ARCHIVED)
        viewModel.state.value
        viewModel.events.test {
            viewModel.onAction(BondSettingsAction.OnCreateInviteClick)
            assertEquals(BondSettingsEvent.Ended, awaitItem())
        }
    }

    @Test
    fun theDailyInviteLimitSaysWhenNotWhy() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER)
        bonds.createAnswers += apiError(429, ApiErrorCode.RATE_LIMITED)

        viewModel.onAction(BondSettingsAction.OnCreateInviteClick)

        assertEquals(error(SettingsField.Invite, Res.string.settings_error_rate_limited), state.error)
    }

    @Test
    fun noInviteCallIsMadeWhileAnotherChangeIsInFlight() = runTest {
        repo.server = settingsV1.copy(status = BondStatus.PENDING_MEMBER, invite = liveInvite)
        repo.gate = CompletableDeferred()
        viewModel.onAction(BondSettingsAction.OnRestDaysToggle)

        viewModel.onAction(BondSettingsAction.OnCancelInviteClick)

        assertTrue(bonds.revoked.isEmpty())
        repo.gate!!.complete(Unit)
    }
}
