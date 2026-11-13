package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.bond.BondType
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.createbond.CreateBondAction
import com.moyi.app.feature.onboarding.createbond.CreateBondEvent
import com.moyi.app.feature.onboarding.createbond.CreateBondStatus
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_create_error
import com.moyi.app.feature.onboarding.resources.bond_create_error_limit
import com.moyi.app.feature.onboarding.resources.bond_name_error_blank
import com.moyi.app.feature.onboarding.resources.bond_name_error_too_long
import com.moyi.app.feature.onboarding.createbond.TimeZoneOption
import com.moyi.app.feature.onboarding.resources.field_timezone_error
import com.moyi.app.feature.onboarding.resources.field_timezone_error_unsupported
import com.moyi.app.feature.onboarding.resources.onboarding_error_generic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateBondViewModelTest : MainDispatcherTest() {
    private val bonds = FakeBondRepository()
    private var zoneIds: () -> Set<String> = { ZONES }
    private val viewModel = createViewModel("Africa/Lagos")

    private fun createViewModel(deviceZone: String) =
        CreateBondViewModel(RelationshipType.Couple, bonds, deviceTimeZone = deviceZone, availableZoneIds = { zoneIds() })

    @Test
    fun aBlankNameIsAFieldErrorAndSendsNothing() = runTest {
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnNameChange("   "))
            viewModel.onAction(CreateBondAction.OnCreateClick)
            expectNoEvents()
        }
        assertEquals(UiText.Resource(Res.string.bond_name_error_blank), viewModel.state.value.nameError)
        assertEquals(emptyList(), bonds.createCalls)
    }

    @Test
    fun typingClearsTheError() {
        viewModel.onAction(CreateBondAction.OnCreateClick)
        viewModel.onAction(CreateBondAction.OnNameChange("A"))
        assertNull(viewModel.state.value.nameError)
    }

    @Test
    fun createSendsTheNameTypeAndDeviceZoneAndHandsOffTheInvite() = runTest {
        viewModel.onAction(CreateBondAction.OnNameChange("Amara and Tunde"))
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToInvite(INVITE.code, INVITE.link, "bond-1", "Amara and Tunde"), awaitItem())
        }
        assertEquals(listOf(FakeBondRepository.CreateCall("Amara and Tunde", BondType.COUPLE, "Africa/Lagos")), bonds.createCalls)
        assertEquals(emptyList(), bonds.inviteCalls, "the 201 carried the invite")
    }

    @Test
    fun whileSendingTheButtonSaysSoAndASecondTapSendsNothing() = runTest {
        bonds.gate = CompletableDeferred()
        viewModel.onAction(CreateBondAction.OnNameChange("Us"))
        viewModel.onAction(CreateBondAction.OnCreateClick)
        assertEquals(CreateBondStatus.Sending, viewModel.state.value.status)
        viewModel.onAction(CreateBondAction.OnCreateClick)
        assertEquals(1, bonds.createCalls.size)
        bonds.gate!!.complete(Unit)
    }

    @Test
    fun aBondWithoutAnInviteAsksForOne() = runTest {
        bonds.createResult = Result.Success(bond(invite = null))
        viewModel.onAction(CreateBondAction.OnNameChange("Us"))
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToInvite(INVITE.code, INVITE.link, "bond-1", "Amara and Tunde"), awaitItem())
        }
        assertEquals(listOf("bond-1"), bonds.inviteCalls)
    }

    @Test
    fun ifTheInviteCannotBeHadARetryAsksOnlyForTheInviteNeverASecondBond() = runTest {
        bonds.createResult = Result.Success(bond(invite = null))
        bonds.inviteResult = Result.Failure(DataError.Network.NoInternet)
        viewModel.onAction(CreateBondAction.OnNameChange("Us"))
        viewModel.onAction(CreateBondAction.OnCreateClick)
        assertEquals(CreateBondStatus.Failed, viewModel.state.value.status)

        bonds.inviteResult = Result.Success(INVITE)
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToInvite(INVITE.code, INVITE.link, "bond-1", "Amara and Tunde"), awaitItem())
        }
        assertEquals(1, bonds.createCalls.size)
        assertEquals(2, bonds.inviteCalls.size)
    }

    @Test
    fun offlineFailsInlineAndKeepsTheName() = runTest {
        bonds.createResult = Result.Failure(DataError.Network.NoInternet)
        viewModel.onAction(CreateBondAction.OnNameChange("Amara and Tunde"))
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            expectNoEvents()
        }
        val state = viewModel.state.value
        assertEquals(CreateBondStatus.Failed, state.status)
        assertEquals(UiText.Resource(Res.string.bond_create_error), state.error)
        assertEquals("Amara and Tunde", state.name)
        assertEquals("Lagos", state.timeZoneCity)
    }

    @Test
    fun aServerErrorIsTheGenericLine() {
        bonds.createResult = Result.Failure(DataError.Network.Server(503))
        viewModel.onAction(CreateBondAction.OnNameChange("Us"))
        viewModel.onAction(CreateBondAction.OnCreateClick)
        assertEquals(UiText.Resource(Res.string.onboarding_error_generic), viewModel.state.value.error)
    }

    @Test
    fun anUnverifiedAccountGoesToTheGate() = runTest {
        bonds.createResult = Result.Failure(problem(403, ApiErrorCode.EMAIL_NOT_VERIFIED))
        viewModel.onAction(CreateBondAction.OnNameChange("Us"))
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToGate, awaitItem())
        }
    }

    @Test
    fun theBondLimitIsSaid() {
        bonds.createResult = Result.Failure(problem(409, ApiErrorCode.BOND_LIMIT_REACHED))
        viewModel.onAction(CreateBondAction.OnNameChange("Us"))
        viewModel.onAction(CreateBondAction.OnCreateClick)
        assertEquals(UiText.Resource(Res.string.bond_create_error_limit), viewModel.state.value.error)
    }

    @Test
    fun aServerRefusalOfTheNameLandsOnTheField() {
        bonds.createResult = Result.Failure(problem(422, ApiErrorCode.VALIDATION_FAILED, "name"))
        viewModel.onAction(CreateBondAction.OnNameChange("Us"))
        viewModel.onAction(CreateBondAction.OnCreateClick)
        assertEquals(UiText.Resource(Res.string.bond_name_error_blank), viewModel.state.value.nameError)
        assertEquals(CreateBondStatus.Idle, viewModel.state.value.status)
    }

    @Test
    fun sixtyCharactersIsAllowed() = runTest {
        viewModel.onAction(CreateBondAction.OnNameChange("a".repeat(60)))
        assertNull(viewModel.state.value.nameError)
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToInvite(INVITE.code, INVITE.link, "bond-1", "Amara and Tunde"), awaitItem())
        }
    }

    @Test
    fun sixtyOneCharactersIsAFieldErrorWhileTypingAndSendsNothing() = runTest {
        val tooLong = UiText.Resource(Res.string.bond_name_error_too_long)
        viewModel.onAction(CreateBondAction.OnNameChange("a".repeat(61)))
        assertEquals(tooLong, viewModel.state.value.nameError)
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            expectNoEvents()
        }
        assertEquals(tooLong, viewModel.state.value.nameError)
        assertEquals("a".repeat(61), viewModel.state.value.name, "the name is kept, not cut")
        assertEquals(emptyList(), bonds.createCalls)
    }

    @Test
    fun aZoneTheBackendRefusesIsSaidOnTheFieldAndNeverSent() {
        for (zone in listOf("UTC", "GMT", "Etc/GMT+1")) {
            val vm = createViewModel(zone)
            vm.onAction(CreateBondAction.OnNameChange("Us"))
            vm.onAction(CreateBondAction.OnCreateClick)
            assertEquals(UiText.Resource(Res.string.field_timezone_error_unsupported), vm.state.value.timeZoneError, zone)
        }
        assertEquals(emptyList(), bonds.createCalls)
    }

    @Test
    fun aServerRefusalOfTheZoneLandsOnTheZoneField() {
        bonds.createResult = Result.Failure(problem(422, ApiErrorCode.VALIDATION_FAILED, "anchorTimezone"))
        viewModel.onAction(CreateBondAction.OnNameChange("Us"))
        viewModel.onAction(CreateBondAction.OnCreateClick)
        assertEquals(UiText.Resource(Res.string.field_timezone_error_unsupported), viewModel.state.value.timeZoneError)
        assertEquals(CreateBondStatus.Idle, viewModel.state.value.status)
    }

    @Test
    fun theTimeZoneShowsAsItsCity() {
        assertEquals("Lagos", viewModel.state.value.timeZoneCity)
        assertEquals("Buenos Aires", CreateBondViewModel.cityOf("America/Argentina/Buenos_Aires"))
    }

    // ---- The time-zone picker ----

    @Test
    fun openingThePickerListsRegionZonesWithTheDeviceZoneFirstAndChosen() {
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        val zones = assertNotNull(viewModel.state.value.picker).zones
        assertEquals("Africa/Lagos", zones.first().id)
        assertTrue(zones.first().selected)
        assertEquals(1, zones.count { it.selected })
        // The rest by city: Buenos Aires, Kolkata, London, Longyearbyen, New York.
        assertEquals(
            listOf("Africa/Lagos", "America/Argentina/Buenos_Aires", "Asia/Kolkata", "Europe/London", "Arctic/Longyearbyen", "America/New_York"),
            zones.map { it.id },
            "no UTC, GMT, Etc/ or legacy US/ links",
        )
        assertEquals(TimeZoneOption("America/Argentina/Buenos_Aires", "Buenos Aires", "America, Argentina", false), zones[1])
    }

    @Test
    fun searchFiltersByCityIgnoringCase() {
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        viewModel.onAction(CreateBondAction.OnTimeZoneQueryChange("lon"))
        val picker = assertNotNull(viewModel.state.value.picker)
        assertEquals("lon", picker.query)
        assertEquals(listOf("London", "Longyearbyen"), picker.zones.map { it.city })

        viewModel.onAction(CreateBondAction.OnTimeZoneQueryChange("Springfield"))
        assertEquals(emptyList(), viewModel.state.value.picker!!.zones)

        viewModel.onAction(CreateBondAction.OnTimeZoneQueryChange(""))
        assertEquals(6, viewModel.state.value.picker!!.zones.size, "clearing the search lists everything again")
    }

    @Test
    fun choosingAZoneSetsItAndClosesThePicker() {
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        viewModel.onAction(CreateBondAction.OnTimeZoneSelect("Europe/London"))
        val state = viewModel.state.value
        assertNull(state.picker)
        assertEquals("Europe/London", state.timeZoneId)
        assertEquals("London", state.timeZoneCity)

        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        val zones = viewModel.state.value.picker!!.zones
        assertEquals(listOf("Europe/London", "Africa/Lagos"), zones.take(2).map { it.id }, "the choice, then the device's")
        assertEquals(listOf("Europe/London"), zones.filter { it.selected }.map { it.id })
    }

    @Test
    fun dismissingThePickerLeavesTheChoiceUnchanged() {
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        viewModel.onAction(CreateBondAction.OnTimeZoneSelect("Europe/London"))
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        viewModel.onAction(CreateBondAction.OnTimeZoneQueryChange("Kol"))
        viewModel.onAction(CreateBondAction.OnTimeZoneDismiss)
        val state = viewModel.state.value
        assertNull(state.picker)
        assertEquals("Europe/London", state.timeZoneId)
        assertEquals("London", state.timeZoneCity)

        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        assertEquals("", viewModel.state.value.picker!!.query, "the search starts empty each time")
    }

    @Test
    fun aZoneNotOfferedCannotBeChosen() {
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        viewModel.onAction(CreateBondAction.OnTimeZoneSelect("Etc/GMT+1"))
        assertEquals("Africa/Lagos", viewModel.state.value.timeZoneId)
    }

    @Test
    fun createSendsTheChosenZone() = runTest {
        viewModel.onAction(CreateBondAction.OnNameChange("Us"))
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        viewModel.onAction(CreateBondAction.OnTimeZoneSelect("America/Argentina/Buenos_Aires"))
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToInvite(INVITE.code, INVITE.link, "bond-1", "Amara and Tunde"), awaitItem())
        }
        assertEquals(
            listOf(FakeBondRepository.CreateCall("Us", BondType.COUPLE, "America/Argentina/Buenos_Aires")),
            bonds.createCalls,
        )
    }

    @Test
    fun choosingARegionZoneClearsTheUnsupportedZoneErrorAndThenCreates() = runTest {
        val vm = createViewModel("UTC")
        vm.onAction(CreateBondAction.OnNameChange("Us"))
        vm.onAction(CreateBondAction.OnCreateClick)
        assertEquals(UiText.Resource(Res.string.field_timezone_error_unsupported), vm.state.value.timeZoneError)

        vm.onAction(CreateBondAction.OnTimeZoneClick)
        assertEquals(
            UiText.Resource(Res.string.field_timezone_error_unsupported),
            vm.state.value.timeZoneError,
            "opening alone does not clear it",
        )
        assertTrue(vm.state.value.picker!!.zones.none { it.id == "UTC" }, "a refused zone is never offered")
        vm.onAction(CreateBondAction.OnTimeZoneSelect("Africa/Lagos"))
        assertNull(vm.state.value.timeZoneError)

        vm.events.test {
            vm.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToInvite(INVITE.code, INVITE.link, "bond-1", "Amara and Tunde"), awaitItem())
        }
        assertEquals(listOf(FakeBondRepository.CreateCall("Us", BondType.COUPLE, "Africa/Lagos")), bonds.createCalls)
    }

    @Test
    fun aLoadFailureIsSaidOnTheFieldAndOpeningAgainRetries() {
        zoneIds = { throw IllegalStateException("no tz database") }
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        assertNull(viewModel.state.value.picker)
        assertEquals(UiText.Resource(Res.string.field_timezone_error), viewModel.state.value.timeZoneError)
        assertEquals("Africa/Lagos", viewModel.state.value.timeZoneId, "the choice is still here")

        zoneIds = { ZONES }
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        assertNotNull(viewModel.state.value.picker)
        assertNull(viewModel.state.value.timeZoneError)
    }

    @Test
    fun aFailureKeepsTheNameAndTheChosenZone() {
        bonds.createResult = Result.Failure(DataError.Network.NoInternet)
        viewModel.onAction(CreateBondAction.OnNameChange("Amara and Tunde"))
        viewModel.onAction(CreateBondAction.OnTimeZoneClick)
        viewModel.onAction(CreateBondAction.OnTimeZoneSelect("Europe/London"))
        viewModel.onAction(CreateBondAction.OnCreateClick)
        val state = viewModel.state.value
        assertEquals(CreateBondStatus.Failed, state.status)
        assertEquals("Amara and Tunde", state.name)
        assertEquals("Europe/London", state.timeZoneId)
        assertEquals("London", state.timeZoneCity)
    }

    private companion object {
        val ZONES = setOf(
            "Africa/Lagos", "Europe/London", "America/New_York", "America/Argentina/Buenos_Aires",
            "Asia/Kolkata", "Arctic/Longyearbyen", "UTC", "GMT", "Etc/GMT+1", "US/Eastern", "SystemV/EST5",
        )
    }
}
