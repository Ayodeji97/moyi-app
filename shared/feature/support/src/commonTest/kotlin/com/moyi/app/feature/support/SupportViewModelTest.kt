package com.moyi.app.feature.support

import app.cash.turbine.test
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.FieldError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.support.SupportRepository
import com.moyi.app.domain.support.SupportText
import com.moyi.app.domain.support.SupportTicket
import com.moyi.app.feature.support.resources.Res
import com.moyi.app.feature.support.resources.support_failed_error
import com.moyi.app.feature.support.resources.support_message_error_blank
import com.moyi.app.feature.support.resources.support_message_error_long
import com.moyi.app.feature.support.resources.support_subject_error_blank
import com.moyi.app.feature.support.resources.support_subject_error_long
import com.moyi.app.feature.support.resources.support_text_error_unsendable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Support: checked before sending, one send at a time, one key per message, and each answer said. */
class SupportViewModelTest : MainDispatcherTest() {
    private class FakeSupport : SupportRepository {
        val sent = mutableListOf<Triple<String, String, String>>()
        val answers = ArrayDeque<Result<SupportTicket, DataError.Network>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun send(subject: String, body: String, idempotencyKey: String): Result<SupportTicket, DataError.Network> {
            sent += Triple(subject, body, idempotencyKey)
            gate?.await()
            return answers.removeFirstOrNull() ?: Result.Success(TICKET)
        }
    }

    private val support = FakeSupport()
    private var keys = 0
    private val viewModel by lazy { SupportViewModel(support, newIdempotencyKey = { "key-${++keys}" }) }
    private val failed = UiText.Resource(Res.string.support_failed_error)

    private fun write(subject: String = "Reveal time", body: String = "The day opened an hour late.") {
        viewModel.onAction(SupportAction.OnSubjectChange(subject))
        viewModel.onAction(SupportAction.OnBodyChange(body))
    }

    private fun apiFailure(status: Int, code: ApiErrorCode, fields: List<String> = emptyList()) =
        Result.Failure(DataError.Network.Api(ApiProblem(status, code, fields.map { FieldError(it, "INVALID", "") }, traceId = "t1")))

    // ----- validation -----

    @Test
    fun blankFieldsAreSaidAndNothingIsSent() {
        write(subject = " ", body = "")
        viewModel.onAction(SupportAction.OnSendClick)

        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.support_subject_error_blank), state.subjectError)
        assertEquals(UiText.Resource(Res.string.support_message_error_blank), state.bodyError)
        assertTrue(support.sent.isEmpty())
    }

    @Test
    fun tooLongNamesTheLimit() {
        write(subject = "a".repeat(SupportText.SUBJECT_MAX + 1), body = "b".repeat(SupportText.BODY_MAX + 1))
        viewModel.onAction(SupportAction.OnSendClick)

        val state = viewModel.state.value
        assertEquals(UiText.Resource(Res.string.support_subject_error_long, listOf(120)), state.subjectError)
        assertEquals(UiText.Resource(Res.string.support_message_error_long, listOf(5000)), state.bodyError)
        assertTrue(support.sent.isEmpty())
    }

    @Test
    fun typingClearsThatFieldsError() {
        write(subject = "", body = "")
        viewModel.onAction(SupportAction.OnSendClick)
        viewModel.onAction(SupportAction.OnSubjectChange("Hello"))

        assertNull(viewModel.state.value.subjectError)
        assertEquals(UiText.Resource(Res.string.support_message_error_blank), viewModel.state.value.bodyError)
    }

    // ----- send -----

    @Test
    fun aSendGoesWithItsWordsAndAKeyThenSaysSent() {
        write()
        viewModel.onAction(SupportAction.OnSendClick)

        assertEquals(listOf(Triple("Reveal time", "The day opened an hour late.", "key-1")), support.sent)
        val state = viewModel.state.value
        assertEquals(SupportStep.Sent, state.step)
        assertFalse(state.sending)
    }

    @Test
    fun whileSendingTheActionSaysSoAndASecondTapDoesNothing() {
        support.gate = CompletableDeferred()
        write()
        viewModel.onAction(SupportAction.OnSendClick)
        assertTrue(viewModel.state.value.sending)

        viewModel.onAction(SupportAction.OnSendClick)
        assertEquals(1, support.sent.size)

        support.gate!!.complete(Unit)
        assertEquals(SupportStep.Sent, viewModel.state.value.step)
        assertEquals(1, support.sent.size)
    }

    @Test
    fun afterSentNothingMoreIsSent() {
        write()
        viewModel.onAction(SupportAction.OnSendClick)
        viewModel.onAction(SupportAction.OnSendClick)

        assertEquals(1, support.sent.size)
    }

    @Test
    fun doneCloses() = runTest {
        viewModel.events.test {
            viewModel.onAction(SupportAction.OnDoneClick)
            assertEquals(SupportEvent.Close, awaitItem())
        }
    }

    // ----- each error -----

    @Test
    fun noInternetKeepsTheWordsAndRetriesWithTheSameKey() {
        support.answers += Result.Failure(DataError.Network.NoInternet)
        write()
        viewModel.onAction(SupportAction.OnSendClick)

        val state = viewModel.state.value
        assertEquals(failed, state.sendError)
        assertEquals(SupportStep.Writing, state.step)
        assertEquals("Reveal time", state.subject)
        assertEquals("The day opened an hour late.", state.body)

        viewModel.onAction(SupportAction.OnSendClick)
        assertEquals(listOf("key-1", "key-1"), support.sent.map { it.third })
        assertEquals(SupportStep.Sent, viewModel.state.value.step)
        assertNull(viewModel.state.value.sendError)
    }

    @Test
    fun changedWordsAfterAFailureGetANewKey() {
        support.answers += Result.Failure(DataError.Network.Timeout)
        write()
        viewModel.onAction(SupportAction.OnSendClick)
        viewModel.onAction(SupportAction.OnBodyChange("The day opened two hours late."))
        viewModel.onAction(SupportAction.OnSendClick)

        assertEquals(listOf("key-1", "key-2"), support.sent.map { it.third })
    }

    @Test
    fun theSixthOfTheDayIsPaused() {
        support.answers += apiFailure(429, ApiErrorCode.RATE_LIMITED)
        write()
        viewModel.onAction(SupportAction.OnSendClick)

        assertEquals(SupportStep.Paused, viewModel.state.value.step)
        assertNull(viewModel.state.value.sendError)
    }

    @Test
    fun aRefusedFieldIsSaidUnderIt() {
        support.answers += apiFailure(422, ApiErrorCode.VALIDATION_FAILED, fields = listOf("body"))
        write()
        viewModel.onAction(SupportAction.OnSendClick)

        val state = viewModel.state.value
        assertNull(state.subjectError)
        assertEquals(UiText.Resource(Res.string.support_text_error_unsendable), state.bodyError)
        assertNull(state.sendError)
        assertFalse(state.sending)
    }

    @Test
    fun aRefusedKeyIsReplacedOnTheNextSend() {
        support.answers += apiFailure(422, ApiErrorCode.VALIDATION_FAILED, fields = listOf("Idempotency-Key"))
        write()
        viewModel.onAction(SupportAction.OnSendClick)
        assertEquals(failed, viewModel.state.value.sendError)

        viewModel.onAction(SupportAction.OnSendClick)
        assertEquals(listOf("key-1", "key-2"), support.sent.map { it.third })
    }

    @Test
    fun aKeyReusedWithOtherWordsIsReplaced() {
        support.answers += apiFailure(422, ApiErrorCode.IDEMPOTENCY_KEY_REUSED)
        write()
        viewModel.onAction(SupportAction.OnSendClick)
        viewModel.onAction(SupportAction.OnSendClick)

        assertEquals(listOf("key-1", "key-2"), support.sent.map { it.third })
    }

    @Test
    fun aKeyStillInFlightIsKeptForTheRetry() {
        support.answers += apiFailure(409, ApiErrorCode.IDEMPOTENCY_KEY_IN_FLIGHT)
        write()
        viewModel.onAction(SupportAction.OnSendClick)
        assertEquals(failed, viewModel.state.value.sendError)

        viewModel.onAction(SupportAction.OnSendClick)
        assertEquals(listOf("key-1", "key-1"), support.sent.map { it.third })
    }

    @Test
    fun aServerFailureKeepsTheWords() {
        support.answers += Result.Failure(DataError.Network.Server(503))
        write()
        viewModel.onAction(SupportAction.OnSendClick)

        assertEquals(failed, viewModel.state.value.sendError)
        assertEquals("Reveal time", viewModel.state.value.subject)
    }

    @Test
    fun anUnexpectedRefusalKeepsTheWords() {
        support.answers += apiFailure(500, ApiErrorCode.INTERNAL_ERROR)
        write()
        viewModel.onAction(SupportAction.OnSendClick)

        assertEquals(failed, viewModel.state.value.sendError)
        assertEquals(SupportStep.Writing, viewModel.state.value.step)
    }

    private companion object {
        val TICKET = SupportTicket("7d8c3b52-5f0c-4c4e-9a52-0b1f3c6d2e11", Instant.parse("2026-11-09T10:15:30Z"))
    }
}
