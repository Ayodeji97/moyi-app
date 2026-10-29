package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.FakeOnboardingData
import com.moyi.app.feature.onboarding.common.InviteCodeInput
import com.moyi.app.feature.onboarding.join.JoinCodeAction
import com.moyi.app.feature.onboarding.join.JoinCodeEvent
import com.moyi.app.feature.onboarding.join.JoinCodeViewModel
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.field_code_error_character
import com.moyi.app.feature.onboarding.resources.field_code_error_length
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinCodeViewModelTest : MainDispatcherTest() {
    private val manual = JoinCodeViewModel(prefilledCode = null)

    @Test
    fun caseDoesNotMatterAndSpacesAndExtraCharactersAreDropped() {
        manual.onAction(JoinCodeAction.OnCodeChange(" k7q 2pd9"))
        assertEquals("K7Q2PD", manual.state.value.code)
        assertNull(manual.state.value.error)
    }

    @Test
    fun anExcludedCharacterIsRejectedWithAMessageNotMapped() {
        // 0 O 1 I L U are not in the alphabet (states.md §2, ADR-0026).
        for (bad in listOf("0", "O", "1", "I", "L", "U", "l", "u")) {
            manual.onAction(JoinCodeAction.OnCodeChange("K7Q$bad"))
            assertEquals(UiText.Resource(Res.string.field_code_error_character), manual.state.value.error, bad)
        }
    }

    @Test
    fun aShortCodeIsCaughtOnContinueNotWhileTyping() = runTest {
        manual.onAction(JoinCodeAction.OnCodeChange("K7Q"))
        assertNull(manual.state.value.error)
        manual.events.test {
            manual.onAction(JoinCodeAction.OnContinueClick)
            expectNoEvents()
        }
        assertEquals(UiText.Resource(Res.string.field_code_error_length), manual.state.value.error)
    }

    @Test
    fun aWellFormedCodeGoesToConfirm() = runTest {
        manual.onAction(JoinCodeAction.OnCodeChange("K7Q2PD"))
        manual.events.test {
            manual.onAction(JoinCodeAction.OnContinueClick)
            assertEquals(JoinCodeEvent.NavigateToConfirm("K7Q2PD"), awaitItem())
        }
    }

    @Test
    fun theFakeUnusableCodeGoesToCannotBeUsed() = runTest {
        manual.onAction(JoinCodeAction.OnCodeChange(FakeOnboardingData.UNUSABLE_CODE))
        manual.events.test {
            manual.onAction(JoinCodeAction.OnContinueClick)
            assertEquals(JoinCodeEvent.NavigateToCannotBeUsed, awaitItem())
        }
    }

    @Test
    fun aCodeFromTheLinkIsPrefilledAndNormalised() {
        val prefilled = JoinCodeViewModel(prefilledCode = "k7q2pd")
        assertTrue(prefilled.state.value.prefilled)
        assertEquals("K7Q2PD", prefilled.state.value.code)
        assertFalse(manual.state.value.prefilled)
    }

    @Test
    fun theAlphabetIsTheThirtySymbolsTheBackendEnforces() {
        assertEquals(30, InviteCodeInput.ALPHABET.length)
        assertTrue(InviteCodeInput.ALPHABET.none { it in "01ILOU" })
    }
}
