package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.FakeOnboardingData
import com.moyi.app.feature.onboarding.common.RelationshipType
import com.moyi.app.feature.onboarding.createbond.CreateBondAction
import com.moyi.app.feature.onboarding.createbond.CreateBondEvent
import com.moyi.app.feature.onboarding.createbond.CreateBondViewModel
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_name_error_blank
import com.moyi.app.feature.onboarding.resources.bond_name_error_too_long
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CreateBondViewModelTest : MainDispatcherTest() {
    private val viewModel = CreateBondViewModel(RelationshipType.Couple)

    @Test
    fun aBlankNameIsAFieldErrorAndGoesNowhere() = runTest {
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnNameChange("   "))
            viewModel.onAction(CreateBondAction.OnCreateClick)
            expectNoEvents()
        }
        assertEquals(UiText.Resource(Res.string.bond_name_error_blank), viewModel.state.value.nameError)
    }

    @Test
    fun typingClearsTheError() {
        viewModel.onAction(CreateBondAction.OnCreateClick)
        viewModel.onAction(CreateBondAction.OnNameChange("A"))
        assertNull(viewModel.state.value.nameError)
    }

    @Test
    fun aNamedBondHandsOffTheFakeInviteCode() = runTest {
        viewModel.onAction(CreateBondAction.OnNameChange("Amara and Tunde"))
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToInvite(FakeOnboardingData.INVITE_CODE), awaitItem())
        }
    }

    @Test
    fun sixtyCharactersIsAllowed() = runTest {
        viewModel.onAction(CreateBondAction.OnNameChange("a".repeat(60)))
        assertNull(viewModel.state.value.nameError)
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            assertEquals(CreateBondEvent.NavigateToInvite(FakeOnboardingData.INVITE_CODE), awaitItem())
        }
    }

    @Test
    fun sixtyOneCharactersIsAFieldErrorWhileTypingAndGoesNowhere() = runTest {
        val tooLong = UiText.Resource(Res.string.bond_name_error_too_long)
        viewModel.onAction(CreateBondAction.OnNameChange("a".repeat(61)))
        assertEquals(tooLong, viewModel.state.value.nameError)
        viewModel.events.test {
            viewModel.onAction(CreateBondAction.OnCreateClick)
            expectNoEvents()
        }
        assertEquals(tooLong, viewModel.state.value.nameError)
        assertEquals("a".repeat(61), viewModel.state.value.name, "the name is kept, not cut")
    }

    @Test
    fun theTimeZoneDefaultsToTheDevicesCity() {
        assertEquals(FakeOnboardingData.TIME_ZONE_CITY, viewModel.state.value.timeZoneCity)
    }
}
