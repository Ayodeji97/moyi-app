package com.moyi.app.feature.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.support.SupportRepository
import com.moyi.app.domain.support.SupportText
import com.moyi.app.domain.support.SupportTextProblem
import com.moyi.app.feature.support.resources.Res
import com.moyi.app.feature.support.resources.support_failed_error
import com.moyi.app.feature.support.resources.support_message_error_blank
import com.moyi.app.feature.support.resources.support_message_error_long
import com.moyi.app.feature.support.resources.support_subject_error_blank
import com.moyi.app.feature.support.resources.support_subject_error_long
import com.moyi.app.feature.support.resources.support_text_error_unsendable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Contact support (FR-102, `POST /support/messages`).
 *
 * - **Checked first**, by the contract's rule ([SupportText]): a blank or too long field
 *   is said under it and nothing is sent.
 * - **One send at a time**: while [SupportState.sending], another tap is ignored.
 * - **One key per message**: the `Idempotency-Key` minted for a subject and body is
 *   reused when the same words are sent again after a failure, so a retry after a lost
 *   answer is the same ticket. Changed words get a new key (the server refuses a key
 *   reused with a different body).
 * - **Answers**: `201` is Sent; `429 RATE_LIMITED` is Paused; `422` naming `subject` or
 *   `body` is said under that field; anything else keeps the words and says it did not
 *   send.
 *
 * @param newIdempotencyKey mints the key; tests pass a counter.
 */
class SupportViewModel(
    private val repository: SupportRepository,
    private val newIdempotencyKey: () -> String = ::randomIdempotencyKey,
) : ViewModel() {
    private val _state = MutableStateFlow(SupportState())
    val state: StateFlow<SupportState> = _state.asStateFlow()

    private val _events = Channel<SupportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The key for the words it was minted for; null after a success or a refused key. */
    private var pending: PendingKey? = null

    fun onAction(action: SupportAction) {
        when (action) {
            is SupportAction.OnSubjectChange -> _state.update { it.copy(subject = action.subject, subjectError = null) }
            is SupportAction.OnBodyChange -> _state.update { it.copy(body = action.body, bodyError = null) }
            SupportAction.OnSendClick -> send()
            SupportAction.OnDoneClick -> viewModelScope.launch { _events.send(SupportEvent.Close) }
        }
    }

    private fun send() {
        val current = _state.value
        if (current.sending || current.step != SupportStep.Writing) return
        val subjectError = SupportText.checkSubject(current.subject)?.toUiText(Field.Subject)
        val bodyError = SupportText.checkBody(current.body)?.toUiText(Field.Body)
        if (subjectError != null || bodyError != null) {
            _state.update { it.copy(subjectError = subjectError, bodyError = bodyError, sendError = null) }
            return
        }
        val key = pending?.takeIf { it.subject == current.subject && it.body == current.body }?.key
            ?: newIdempotencyKey().also { pending = PendingKey(it, current.subject, current.body) }
        _state.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            when (val result = repository.send(current.subject, current.body, key)) {
                is Result.Success -> {
                    pending = null
                    _state.update { it.copy(sending = false, step = SupportStep.Sent) }
                }
                is Result.Failure -> failed(result.error)
            }
        }
    }

    private fun failed(error: DataError.Network) {
        val problem = (error as? DataError.Network.Api)?.problem
        when (problem?.code) {
            ApiErrorCode.RATE_LIMITED -> {
                _state.update { it.copy(sending = false, step = SupportStep.Paused) }
                return
            }
            ApiErrorCode.VALIDATION_FAILED -> {
                val fields = problem.fieldErrors.map { it.field }.toSet()
                val unsendable = UiText.Resource(Res.string.support_text_error_unsendable)
                if ("subject" in fields || "body" in fields) {
                    _state.update {
                        it.copy(
                            sending = false,
                            subjectError = unsendable.takeIf { "subject" in fields },
                            bodyError = unsendable.takeIf { "body" in fields },
                        )
                    }
                    return
                }
                // The key itself was refused: the next send mints another.
                pending = null
            }
            // The same key with other words: a fresh key, and the next tap sends.
            ApiErrorCode.IDEMPOTENCY_KEY_REUSED -> pending = null
            // In flight (409) keeps its key: the retry is answered with the first attempt's ticket.
            else -> Unit
        }
        _state.update { it.copy(sending = false, sendError = UiText.Resource(Res.string.support_failed_error)) }
    }

    private enum class Field { Subject, Body }

    private fun SupportTextProblem.toUiText(field: Field): UiText = when (this) {
        SupportTextProblem.Blank -> UiText.Resource(
            if (field == Field.Subject) Res.string.support_subject_error_blank else Res.string.support_message_error_blank,
        )
        SupportTextProblem.TooLong -> if (field == Field.Subject) {
            UiText.Resource(Res.string.support_subject_error_long, listOf(SupportText.SUBJECT_MAX))
        } else {
            UiText.Resource(Res.string.support_message_error_long, listOf(SupportText.BODY_MAX))
        }
        SupportTextProblem.Unsendable -> UiText.Resource(Res.string.support_text_error_unsendable)
    }

    private class PendingKey(val key: String, val subject: String, val body: String)
}

@OptIn(ExperimentalUuidApi::class)
internal fun randomIdempotencyKey(): String = Uuid.random().toString()
