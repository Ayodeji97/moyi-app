package com.moyi.app.feature.bond

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.account.AccountDeletionRepository
import com.moyi.app.domain.account.DeletionRequest
import com.moyi.app.feature.bond.account.AccountAction
import com.moyi.app.feature.bond.account.AccountEvent
import com.moyi.app.feature.bond.account.AccountLoad
import com.moyi.app.feature.bond.account.AccountViewModel
import com.moyi.app.feature.bond.account.DeletionCall
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.delete_account_error_blank
import com.moyi.app.feature.bond.resources.delete_account_error_password
import com.moyi.app.feature.bond.resources.delete_account_error_rate_limited
import com.moyi.app.feature.bond.resources.delete_account_error_too_late
import com.moyi.app.feature.bond.resources.settings_error_generic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

/** Account and Delete your account: load, the password, each refusal, one call at a time, and keeping the account. */
class AccountViewModelTest : MainDispatcherTest() {
    private class FakeDeletion : AccountDeletionRepository {
        var server: DeletionRequest? = null
        val pendingReads = ArrayDeque<Result<DeletionRequest?, DataError.Network>>()
        val requestAnswers = ArrayDeque<Result<DeletionRequest, DataError.Network>>()
        val cancelAnswers = ArrayDeque<EmptyResult<DataError.Network>>()
        val passwords = mutableListOf<String>()
        var cancels = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun pending(): Result<DeletionRequest?, DataError.Network> = pendingReads.removeFirstOrNull() ?: Result.Success(server)

        override suspend fun request(password: String): Result<DeletionRequest, DataError.Network> {
            passwords += password
            gate?.await()
            requestAnswers.removeFirstOrNull()?.let { return it }
            return Result.Success(pending).also { server = pending }
        }

        override suspend fun cancel(): EmptyResult<DataError.Network> {
            cancels++
            gate?.await()
            cancelAnswers.removeFirstOrNull()?.let { return it }
            server = null
            return Result.Success(Unit)
        }
    }

    private val deletion = FakeDeletion()
    private val viewModel by lazy { AccountViewModel(deletion) }

    private fun api(status: Int, code: ApiErrorCode) = Result.Failure(DataError.Network.Api(ApiProblem(status, code)))

    // ----- load -----

    @Test
    fun loadsWithNothingPending() {
        assertEquals(AccountLoad.Ready, viewModel.state.value.load)
        assertNull(viewModel.state.value.pending)
    }

    @Test
    fun aPendingDeletionFromMeIsShownAfterARestart() {
        deletion.server = pending
        assertEquals(pending, viewModel.state.value.pending)
    }

    @Test
    fun aFailedLoadIsRetried() {
        deletion.pendingReads += Result.Failure(DataError.Network.NoInternet)
        assertEquals(AccountLoad.Failed, viewModel.state.value.load)

        viewModel.onAction(AccountAction.OnRetryClick)

        assertEquals(AccountLoad.Ready, viewModel.state.value.load)
    }

    // ----- asking -----

    @Test
    fun theRightPasswordAsksAndTheScreenTurnsToThePendingDeletion() {
        viewModel.onAction(AccountAction.OnPasswordChange("correct horse"))
        viewModel.onAction(AccountAction.OnConfirmDeletion)

        val state = viewModel.state.value
        assertEquals(listOf("correct horse"), deletion.passwords)
        assertEquals(pending, state.pending)
        assertEquals("", state.password, "the password is dropped once used")
        assertNull(state.inFlight)
    }

    @Test
    fun aBlankPasswordIsRefusedBeforeAnyRequest() {
        viewModel.onAction(AccountAction.OnPasswordChange("   "))
        viewModel.onAction(AccountAction.OnConfirmDeletion)

        assertEquals(emptyList(), deletion.passwords)
        assertEquals(UiText.Resource(Res.string.delete_account_error_blank), viewModel.state.value.passwordError)
    }

    @Test
    fun aWrongPasswordIsTheFieldsErrorAndTheInputIsKept() {
        deletion.requestAnswers += api(401, ApiErrorCode.INVALID_CREDENTIALS)
        viewModel.onAction(AccountAction.OnPasswordChange("wrong"))
        viewModel.onAction(AccountAction.OnConfirmDeletion)

        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.delete_account_error_password), state.passwordError)
        assertEquals("wrong", state.password)
        assertNull(state.pending)
        assertNull(state.inFlight)

        viewModel.onAction(AccountAction.OnPasswordChange("wrong2"))
        assertNull(viewModel.state.value.passwordError, "typing again clears it")
    }

    @Test
    fun theRateLimitAndOtherFailuresAreSaidAboveTheAction() {
        deletion.requestAnswers += api(429, ApiErrorCode.RATE_LIMITED)
        deletion.requestAnswers += Result.Failure(DataError.Network.NoInternet)
        viewModel.onAction(AccountAction.OnPasswordChange("pw"))

        viewModel.onAction(AccountAction.OnConfirmDeletion)
        assertEquals(UiText.Resource(Res.string.delete_account_error_rate_limited), viewModel.state.value.error)

        viewModel.onAction(AccountAction.OnConfirmDeletion)
        assertEquals(UiText.Resource(Res.string.settings_error_generic), viewModel.state.value.error)
        assertEquals("pw", viewModel.state.value.password)
    }

    @Test
    fun aDoubleTapSendsOneRequest() {
        deletion.gate = CompletableDeferred()
        viewModel.onAction(AccountAction.OnPasswordChange("pw"))

        viewModel.onAction(AccountAction.OnConfirmDeletion)
        viewModel.onAction(AccountAction.OnConfirmDeletion)
        assertEquals(DeletionCall.Request, viewModel.state.value.inFlight)
        deletion.gate?.complete(Unit)

        assertEquals(1, deletion.passwords.size)
        assertEquals(pending, viewModel.state.value.pending)
    }

    @Test
    fun closingTheDeleteScreenForgetsThePassword() {
        viewModel.onAction(AccountAction.OnPasswordChange("pw"))
        viewModel.onAction(AccountAction.OnDeleteClosed)

        assertEquals("", viewModel.state.value.password)
    }

    @Test
    fun thePasswordIsNotInTheStatesText() {
        viewModel.onAction(AccountAction.OnPasswordChange("hunter2-secret"))

        assertFalse("hunter2-secret" in viewModel.state.value.toString())
    }

    // ----- keeping the account -----

    @Test
    fun keepingTheAccountCancelsAndReturnsToAccount() = runTest {
        deletion.server = pending
        viewModel.events.test {
            viewModel.onAction(AccountAction.OnKeepAccount)

            assertEquals(AccountEvent.DeletionCancelled, awaitItem())
            assertEquals(1, deletion.cancels)
            assertNull(viewModel.state.value.pending)
        }
    }

    @Test
    fun aDoubleTapOnKeepCancelsOnce() {
        deletion.server = pending
        deletion.gate = CompletableDeferred()
        viewModel.state.value // load

        viewModel.onAction(AccountAction.OnKeepAccount)
        viewModel.onAction(AccountAction.OnKeepAccount)
        deletion.gate?.complete(Unit)

        assertEquals(1, deletion.cancels)
    }

    @Test
    fun nothingToCancelWithTheDeletionStillThereIsTooLate() {
        deletion.server = pending
        deletion.cancelAnswers += api(404, ApiErrorCode.NOT_FOUND)
        viewModel.state.value

        viewModel.onAction(AccountAction.OnKeepAccount)

        assertEquals(UiText.Resource(Res.string.delete_account_error_too_late), viewModel.state.value.error)
        assertEquals(pending, viewModel.state.value.pending)
    }

    @Test
    fun nothingToCancelBecauseItWasCalledOffElsewhereReturnsToAccount() = runTest {
        deletion.server = pending
        viewModel.state.value
        deletion.server = null
        deletion.cancelAnswers += api(404, ApiErrorCode.NOT_FOUND)

        viewModel.events.test {
            viewModel.onAction(AccountAction.OnKeepAccount)
            assertEquals(AccountEvent.DeletionCancelled, awaitItem())
        }
        assertNull(viewModel.state.value.pending)
    }

    private companion object {
        val pending = DeletionRequest(
            requestedAt = Instant.parse("2026-11-09T10:00:00Z"),
            cancellableUntil = Instant.parse("2026-11-23T10:00:00Z"),
            deleteBy = Instant.parse("2026-12-09T10:00:00Z"),
        )
    }
}
