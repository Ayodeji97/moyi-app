package com.moyi.app.feature.onboarding

import app.cash.turbine.test
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.onboarding.common.InviteCodeInput
import com.moyi.app.feature.onboarding.join.JoinCodeAction
import com.moyi.app.feature.onboarding.join.JoinCodeEvent
import com.moyi.app.feature.onboarding.join.JoinCodeViewModel
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.field_code_error_character
import com.moyi.app.feature.onboarding.resources.field_code_error_length
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.feature.onboarding.resources.bond_create_error
import com.moyi.app.feature.onboarding.resources.onboarding_error_rate_limited
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JoinCodeViewModelTest : MainDispatcherTest() {
    private val bonds = FakeBondRepository()
    private val manual = JoinCodeViewModel(prefilledCode = null, bonds = bonds)

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
    fun aShortCodeIsNeverSent() {
        manual.onAction(JoinCodeAction.OnCodeChange("K7Q"))
        manual.onAction(JoinCodeAction.OnContinueClick)
        assertEquals(emptyList(), bonds.resolveCalls)
    }

    @Test
    fun aCodeThatResolvesGoesToConfirmWithItsPreview() = runTest {
        manual.onAction(JoinCodeAction.OnCodeChange("K7Q2PD"))
        manual.events.test {
            manual.onAction(JoinCodeAction.OnContinueClick)
            assertEquals(JoinCodeEvent.NavigateToConfirm("K7Q2PD", PREVIEW), awaitItem())
        }
        assertEquals(listOf("K7Q2PD"), bonds.resolveCalls)
    }

    @Test
    fun whileCheckingTheButtonSaysSoAndNothingIsSentTwice() = runTest {
        bonds.gate = CompletableDeferred()
        manual.onAction(JoinCodeAction.OnCodeChange("K7Q2PD"))
        manual.onAction(JoinCodeAction.OnContinueClick)
        assertTrue(manual.state.value.checking)
        manual.onAction(JoinCodeAction.OnContinueClick)
        manual.onAction(JoinCodeAction.OnCodeChange("AAAAAA"))
        assertEquals("K7Q2PD", manual.state.value.code, "the code cannot change under a request")
        assertEquals(1, bonds.resolveCalls.size)
        bonds.gate!!.complete(Unit)
        assertFalse(manual.state.value.checking)
    }

    @Test
    fun aCodeThatCannotBeUsedGoesToTheOneScreenForEveryCause() = runTest {
        bonds.resolveResult = Result.Failure(problem(404, ApiErrorCode.INVITE_NOT_USABLE))
        manual.onAction(JoinCodeAction.OnCodeChange("ZZZZZZ"))
        manual.events.test {
            manual.onAction(JoinCodeAction.OnContinueClick)
            assertEquals(JoinCodeEvent.NavigateToCannotBeUsed, awaitItem())
        }
    }

    @Test
    fun theServersShapeRefusalIsTheFieldError() {
        bonds.resolveResult = Result.Failure(problem(422, ApiErrorCode.VALIDATION_FAILED, "code"))
        manual.onAction(JoinCodeAction.OnCodeChange("K7Q2PD"))
        manual.onAction(JoinCodeAction.OnContinueClick)
        assertEquals(UiText.Resource(Res.string.field_code_error_character), manual.state.value.error)
    }

    @Test
    fun offlineIsInlineAndKeepsTheCode() = runTest {
        bonds.resolveResult = Result.Failure(DataError.Network.NoInternet)
        manual.onAction(JoinCodeAction.OnCodeChange("K7Q2PD"))
        manual.events.test {
            manual.onAction(JoinCodeAction.OnContinueClick)
            expectNoEvents()
        }
        assertEquals(UiText.Resource(Res.string.bond_create_error), manual.state.value.failure)
        assertEquals("K7Q2PD", manual.state.value.code)
    }

    @Test
    fun rateLimitedSaysWhenNotWhy() {
        bonds.resolveResult = Result.Failure(problem(429, ApiErrorCode.RATE_LIMITED))
        manual.onAction(JoinCodeAction.OnCodeChange("K7Q2PD"))
        manual.onAction(JoinCodeAction.OnContinueClick)
        assertEquals(UiText.Resource(Res.string.onboarding_error_rate_limited), manual.state.value.failure)
    }

    @Test
    fun aPastedLinkGivesItsCode() {
        manual.onAction(JoinCodeAction.OnCodeChange("https://getmoyi.app/i/k7q2pd"))
        assertEquals("K7Q2PD", manual.state.value.code)
        manual.onAction(JoinCodeAction.OnCodeChange("getmoyi.app/join/K7Q2PD/?utm=x#top"))
        assertEquals("K7Q2PD", manual.state.value.code)
        manual.onAction(JoinCodeAction.OnCodeChange("  Here: https://getmoyi.app/i/K7Q2PD\n"))
        assertEquals("K7Q2PD", manual.state.value.code)
    }

    @Test
    fun aCodeFromTheLinkIsPrefilledAndNormalised() {
        val prefilled = JoinCodeViewModel(prefilledCode = "k7q2pd", bonds = bonds)
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
