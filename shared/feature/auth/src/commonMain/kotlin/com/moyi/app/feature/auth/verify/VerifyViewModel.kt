package com.moyi.app.feature.auth.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.feature.auth.common.AuthErrorMapper
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.extractVerificationToken
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.verify_token_error_blank
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

class VerifyViewModel(
    email: String,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(VerifyState(email = email))
    val state: StateFlow<VerifyState> = _state.asStateFlow()

    private val _events = Channel<VerifyEvent>()
    val events = _events.receiveAsFlow()

    private var cooldown: Job? = null

    fun onAction(action: VerifyAction) {
        when (action) {
            is VerifyAction.OnTokenChange -> _state.update {
                // A new paste clears the old verdict; there is nothing to re-check client-side.
                it.copy(token = it.token.copy(value = action.value, error = null))
            }
            VerifyAction.OnSubmit -> submit()
            VerifyAction.OnResendClick -> resend()
            VerifyAction.OnChangeEmailClick, VerifyAction.OnBackClick ->
                viewModelScope.launch { _events.send(VerifyEvent.NavigateBack) }
        }
    }

    private fun submit() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending || current.mode != VerifyMode.Waiting) return
        val token = extractVerificationToken(current.token.value)
        if (token.isEmpty()) {
            _state.update {
                it.copy(token = it.token.copy(showErrors = true, error = UiText.Resource(Res.string.verify_token_error_blank)))
            }
            return
        }
        // A new check supersedes an old resend failure: one message at a time above the action.
        _state.update { it.copy(submit = SubmitStatus.Sending, resendFailure = null) }
        viewModelScope.launch {
            when (val result = authRepository.verifyEmail(token)) {
                is Result.Success -> {
                    _state.update { it.copy(submit = SubmitStatus.Idle) }
                    _events.send(VerifyEvent.NavigateToLogin(current.email))
                }
                is Result.Failure -> {
                    val tokenError = AuthErrorMapper.verifyTokenError(result.error)
                    _state.update {
                        when {
                            AuthErrorMapper.isVerificationExpired(result.error) ->
                                it.copy(mode = VerifyMode.Expired, submit = SubmitStatus.Idle)
                            tokenError != null ->
                                it.copy(token = it.token.copy(showErrors = true, error = tokenError), submit = SubmitStatus.Idle)
                            else -> it.copy(submit = AuthErrorMapper.formFailure(result.error))
                        }
                    }
                }
            }
        }
    }

    /**
     * Resend always answers 202 on a reachable server, known address or not
     * (doc 06 §3.1). From the expired state a new link returns the screen to
     * waiting, with the paste field cleared for the new code.
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
                            it.copy(
                                mode = VerifyMode.Waiting,
                                token = it.token.copy(value = "", error = null),
                                resend = ResendStatus.Sent,
                                submit = SubmitStatus.Idle,
                            )
                        } else {
                            it.copy(resend = ResendStatus.Sent)
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
