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
import com.moyi.app.domain.today.QueuedEntry
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
import kotlin.test.assertTrue
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
    private val auth = FakeAuthRepository()
    private val outbox = FakeOutbox()
    private val viewModel by lazy { AccountViewModel(deletion, auth, outbox, outbox) }

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
        deletion.requestAnswers += api(403, ApiErrorCode.INVALID_CREDENTIALS)
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

    // ----- sign out: ONE question, chosen by whether words would be lost -----

    @Test
    fun nothingUnsentAsksOnlySignOutAndCancelKeepsYouSignedIn() {
        viewModel.onAction(AccountAction.OnSignOutClick)

        assertTrue(viewModel.state.value.confirmingSignOut)
        assertFalse(viewModel.state.value.confirmingUnsentSignOut, "never both questions")
        assertFalse(viewModel.state.value.signingOut)
        assertEquals(0, auth.logouts, "the row alone signs nobody out")
        assertEquals(0, outbox.passes, "nothing waiting: no send")

        viewModel.onAction(AccountAction.OnSignOutDismiss)
        assertFalse(viewModel.state.value.confirmingSignOut)
        assertEquals(0, auth.logouts)
    }

    @Test
    fun nothingUnsentConfirmSignsOutOnce() {
        viewModel.onAction(AccountAction.OnSignOutClick)
        viewModel.onAction(AccountAction.OnSignOutConfirm)
        viewModel.onAction(AccountAction.OnSignOutConfirm)

        assertFalse(viewModel.state.value.confirmingSignOut)
        assertTrue(viewModel.state.value.signingOut)
        assertEquals(1, auth.logouts)
    }

    @Test
    fun signOutSendsWhatIsWaitingFirstAndStillAsksOnlySignOutWhenItAllWent() {
        outbox.queue += waiting
        outbox.online = true

        viewModel.onAction(AccountAction.OnSignOutClick)

        assertEquals(1, outbox.passes)
        assertTrue(viewModel.state.value.confirmingSignOut)
        assertFalse(viewModel.state.value.confirmingUnsentSignOut)
        assertEquals(0, auth.logouts)
    }

    @Test
    fun unsentWordsAskOnlyTheUnsentQuestionAndStayingKeepsThem() {
        outbox.queue += waiting

        viewModel.onAction(AccountAction.OnSignOutClick)
        assertTrue(viewModel.state.value.confirmingUnsentSignOut)
        assertFalse(viewModel.state.value.confirmingSignOut, "never both questions")
        assertFalse(viewModel.state.value.signingOut)
        assertEquals(1, outbox.passes, "one more try before asking")
        assertEquals(0, auth.logouts, "not one word deleted without asking")

        viewModel.onAction(AccountAction.OnSignOutUnsentDismiss)
        assertFalse(viewModel.state.value.confirmingUnsentSignOut)
        assertEquals(0, auth.logouts)
        assertEquals(1, outbox.queue.size)

        viewModel.onAction(AccountAction.OnSignOutClick)
        viewModel.onAction(AccountAction.OnSignOutUnsentConfirm)
        assertEquals(1, auth.logouts, "the person chose: the session (and its wipe) ends")
    }

    @Test
    fun offlineWithUnsentWordsAsksOnlyTheUnsentQuestionAndConfirmSignsOut() {
        deletion.pendingReads += Result.Failure(DataError.Network.NoInternet)
        outbox.online = false
        outbox.queue += waiting
        assertEquals(AccountLoad.Failed, viewModel.state.value.load)

        viewModel.onAction(AccountAction.OnSignOutClick)
        assertTrue(viewModel.state.value.confirmingUnsentSignOut)
        assertFalse(viewModel.state.value.confirmingSignOut)

        viewModel.onAction(AccountAction.OnSignOutUnsentConfirm)
        assertEquals(1, auth.logouts, "offline sign-out (#41) still ends the local session")
    }

    @Test
    fun offlineWithNothingUnsentAsksSignOutAndConfirmSignsOut() {
        deletion.pendingReads += Result.Failure(DataError.Network.NoInternet)
        outbox.online = false

        viewModel.onAction(AccountAction.OnSignOutClick)
        assertTrue(viewModel.state.value.confirmingSignOut)
        assertEquals(0, auth.logouts)

        viewModel.onAction(AccountAction.OnSignOutConfirm)
        assertEquals(1, auth.logouts)
    }

    @Test
    fun whileSigningOutExportAndDeleteDoNotOpen() = runTest {
        outbox.queue += waiting
        outbox.online = true
        signOutConfirmed()

        viewModel.events.test {
            viewModel.onAction(AccountAction.OnExportClick)
            viewModel.onAction(AccountAction.OnDeleteClick)
            expectNoEvents()
        }
    }

    private fun signOutConfirmed() {
        viewModel.onAction(AccountAction.OnSignOutClick)
        viewModel.onAction(AccountAction.OnSignOutConfirm)
    }

    private val waiting = QueuedEntry("b1", "You fixed the gate.", null, "key-1", Instant.parse("2026-09-14T11:00:00Z"), "2026-09-14")

    private companion object {
        val pending = DeletionRequest(
            requestedAt = Instant.parse("2026-11-09T10:00:00Z"),
            cancellableUntil = Instant.parse("2026-11-23T10:00:00Z"),
            deleteBy = Instant.parse("2026-12-09T10:00:00Z"),
        )
    }
}
