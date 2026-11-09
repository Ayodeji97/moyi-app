package com.moyi.app.feature.auth.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.EmailProof
import com.moyi.app.feature.auth.common.AuthErrorMapper
import com.moyi.app.feature.auth.common.CodeInput
import com.moyi.app.feature.auth.common.EMAIL_CODE_LENGTH
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.parseCodeInput
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.code_error_blank
import com.moyi.app.feature.auth.resources.code_error_no_address
import com.moyi.app.feature.auth.resources.code_error_short
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * [linkToken] is the token from the emailed link when the link opened the app
 * (`https://getmoyi.app/verify?token=…`, DeepLink): it is checked at once, so the person
 * sees the screen's own verdict (signed in next, expired, or the field's error) without
 * typing anything. It is never shown in the code field, which holds only digits.
 *
 * The code field (docs/missing-components.md §58) takes six digits typed from the email or
 * a pasted link. The sixth digit checks the code by itself, once: a code the server refused
 * is not sent again until it is changed, since each wrong try spends one of its five.
 */
class VerifyViewModel(
    email: String,
    private val authRepository: AuthRepository,
    linkToken: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(VerifyState(email = email))
    val state: StateFlow<VerifyState> = _state.asStateFlow()

    private val _events = Channel<VerifyEvent>()
    val events = _events.receiveAsFlow()

    private var cooldown: Job? = null

    init {
        if (linkToken != null) sendProof(EmailProof.Link(linkToken))
    }

    fun onAction(action: VerifyAction) {
        when (action) {
            is VerifyAction.OnCodeChange -> onCodeChange(action.value)
            VerifyAction.OnSubmit -> submitCode()
            VerifyAction.OnResendClick -> resend()
            VerifyAction.OnSignInClick -> viewModelScope.launch { _events.send(VerifyEvent.NavigateToSignIn) }
            VerifyAction.OnChangeEmailClick, VerifyAction.OnBackClick ->
                viewModelScope.launch { _events.send(VerifyEvent.NavigateBack) }
        }
    }

    /**
     * Keystrokes are reduced to digits, six at most. A pasted link is checked as the link
     * would be when opened. Ignored while a check is in flight: the field keeps showing what
     * is being checked.
     */
    private fun onCodeChange(raw: String) {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending || current.mode != VerifyMode.Waiting) return
        when (val input = parseCodeInput(raw)) {
            is CodeInput.Link -> {
                _state.update { it.copy(code = FieldInput()) }
                sendProof(EmailProof.Link(input.token))
            }
            is CodeInput.Digits -> {
                // A letter typed, or a seventh digit: nothing changed, and an error stays put.
                if (input.value == current.code.value) return
                // A new digit clears the old verdict.
                _state.update { it.copy(code = FieldInput(value = input.value)) }
                if (input.value.length == EMAIL_CODE_LENGTH) submitCode()
            }
        }
    }

    private fun submitCode() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending || current.mode != VerifyMode.Waiting) return
        val digits = current.code.value
        // This exact code was refused; sending it again would only spend another try.
        if (digits.length == EMAIL_CODE_LENGTH && current.code.error != null) return
        val fieldError = when {
            digits.isEmpty() -> Res.string.code_error_blank
            digits.length < EMAIL_CODE_LENGTH -> Res.string.code_error_short
            // Opened by a link on a phone that does not know the address: a code names no account.
            !current.addressKnown -> Res.string.code_error_no_address
            else -> null
        }
        if (fieldError != null) {
            _state.update { it.copy(code = it.code.copy(showErrors = true, error = UiText.Resource(fieldError))) }
            return
        }
        sendProof(EmailProof.Code(email = current.email, code = digits))
    }

    private fun sendProof(proof: EmailProof) {
        // A new check supersedes an old resend failure: one message at a time above the action.
        _state.update { it.copy(submit = SubmitStatus.Sending, resendFailure = null) }
        viewModelScope.launch {
            when (val result = authRepository.verifyEmail(proof)) {
                is Result.Success -> {
                    _state.update { it.copy(submit = SubmitStatus.Idle) }
                    _events.send(VerifyEvent.NavigateToLogin(_state.value.email))
                }
                is Result.Failure -> {
                    val fieldError = AuthErrorMapper.verifyProofError(result.error, proof)
                    _state.update {
                        when {
                            AuthErrorMapper.isVerificationExpired(result.error) ->
                                it.copy(mode = VerifyMode.Expired, code = FieldInput(), submit = SubmitStatus.Idle)
                            fieldError != null ->
                                it.copy(code = it.code.copy(showErrors = true, error = fieldError), submit = SubmitStatus.Idle)
                            else -> it.copy(submit = AuthErrorMapper.proofFormFailure(result.error, proof))
                        }
                    }
                }
            }
        }
    }

    /**
     * Resend always answers 202 on a reachable server, known address or not
     * (doc 06 §3.1). A new email voids the old code (the contract: a code is
     * good "until a newer email is sent"), so the code field is cleared either
     * way, and from the expired state the screen returns to waiting.
     *
     * The server allows one resend a minute (ADR-0023) whichever button asks,
     * so a link sent in the last minute ([ResendStatus.Sent]) makes both
     * "Send it again" and the expired state's "Send a new link" wait.
     *
     * Two buttons, two statuses. From the expired state the resend IS the
     * primary action, so it drives [VerifyState.submit]. While waiting,
     * "Send it again" drives only [VerifyState.resend] and its own
     * [VerifyState.resendFailure]: it never touches the check's status, so it
     * can neither reopen "Check again" mid-check nor overwrite its verdict.
     * It is held while a check is in flight.
     */
    private fun resend() {
        val current = _state.value
        // Opened by a link with no address: there is nowhere to resend to (VerifyState.addressKnown).
        if (!current.addressKnown) return
        val fromExpired = current.mode == VerifyMode.Expired
        val coolingDown = current.resend == ResendStatus.Sent
        val inFlight = current.submit == SubmitStatus.Sending || (!fromExpired && current.resend == ResendStatus.Sending)
        if (coolingDown || inFlight) return

        _state.update {
            if (fromExpired) it.copy(submit = SubmitStatus.Sending) else it.copy(resend = ResendStatus.Sending, resendFailure = null)
        }
        viewModelScope.launch {
            when (val result = authRepository.resendVerification(current.email)) {
                is Result.Success -> {
                    _state.update {
                        if (fromExpired) {
                            it.copy(mode = VerifyMode.Waiting, code = FieldInput(), resend = ResendStatus.Sent, submit = SubmitStatus.Idle)
                        } else {
                            it.copy(code = FieldInput(), resend = ResendStatus.Sent)
                        }
                    }
                    startCooldown()
                }
                is Result.Failure -> _state.update {
                    val failure = AuthErrorMapper.formFailure(result.error)
                    if (fromExpired) it.copy(submit = failure) else it.copy(resend = ResendStatus.Ready, resendFailure = failure.message)
                }
            }
        }
    }

    /** "You can ask for another in a minute": the link comes back after one. */
    private fun startCooldown() {
        cooldown?.cancel()
        cooldown = viewModelScope.launch {
            delay(RESEND_COOLDOWN)
            _state.update { if (it.resend == ResendStatus.Sent) it.copy(resend = ResendStatus.Ready) else it }
        }
    }

    private companion object {
        /** Matches the server's limit of one resend a minute (ADR-0023) and the `verify.resent` copy. */
        val RESEND_COOLDOWN = 1.minutes
    }
}
