package com.moyi.app.feature.auth.reset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.EmailProof
import com.moyi.app.feature.auth.common.AuthErrorMapper
import com.moyi.app.feature.auth.common.AuthValidation
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.feature.auth.common.CodeInput
import com.moyi.app.feature.auth.common.EMAIL_CODE_LENGTH
import com.moyi.app.feature.auth.common.parseCodeInput
import com.moyi.app.feature.auth.register.blurred
import com.moyi.app.feature.auth.register.edited
import com.moyi.app.feature.auth.register.validated
import com.moyi.app.feature.auth.register.withServerError
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.code_error_blank
import com.moyi.app.feature.auth.resources.code_error_no_address
import com.moyi.app.feature.auth.resources.code_error_short
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Two steps (backend-contract-gaps.md row 39). First the code: six digits typed from the
 * email, checked by the server on its own (`POST /auth/reset-password/check`) on the sixth
 * digit or on "Check the code", so a wrong or expired code shows on the code field before
 * any password is typed. Only a right code opens the second step, the new password, which
 * is sent with the same code. The check grants nothing: the reset can still refuse the code
 * if it expired or was replaced in between, and then the screen returns to the code.
 *
 * [linkToken] is the token from the emailed link when the link opened the app
 * (`https://getmoyi.app/reset-password?token=…`, DeepLink): it is the proof, there is no
 * check for a link, and the screen starts on the password step. A link pasted into the code
 * field does the same. Nothing is sent until the person chooses the password.
 *
 * Back from the password returns to the code, unless the link opened the app: there is no
 * code step to return to, so Back leaves the screen.
 */
class ResetPasswordViewModel(
    email: String,
    private val authRepository: AuthRepository,
    linkToken: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ResetPasswordState(email = email, linkToken = linkToken))
    val state: StateFlow<ResetPasswordState> = _state.asStateFlow()

    private val _events = Channel<ResetPasswordEvent>()
    val events = _events.receiveAsFlow()

    private val openedByLink = linkToken != null

    fun onAction(action: ResetPasswordAction) {
        when (action) {
            is ResetPasswordAction.OnCodeChange -> onCodeChange(action.value)
            is ResetPasswordAction.OnPasswordChange ->
                _state.update { it.copy(password = it.password.edited(action.value, AuthValidation::newPassword)) }
            ResetPasswordAction.OnPasswordBlur ->
                _state.update { it.copy(password = it.password.blurred(AuthValidation::newPassword)) }
            ResetPasswordAction.OnSubmit ->
                if (_state.value.step == ResetStep.Code) checkCode() else submit()
            ResetPasswordAction.OnBackClick -> onBack()
        }
    }

    private fun onBack() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending) return
        if (current.step == ResetStep.Password && !openedByLink) {
            // Back to the code, which stays as typed. A pasted link is dropped, so the field
            // takes a code again. The password typed so far goes: it was for that code.
            _state.update {
                it.copy(step = ResetStep.Code, linkToken = null, password = FieldInput(), submit = SubmitStatus.Idle)
            }
        } else {
            viewModelScope.launch { _events.send(ResetPasswordEvent.NavigateBack) }
        }
    }

    /**
     * Digits, six at most, or a pasted link, which becomes the proof as an opened link
     * would and opens the password step. The sixth digit checks the code. Ignored while a
     * check is in flight: the field keeps showing what was sent.
     */
    private fun onCodeChange(raw: String) {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending || current.step != ResetStep.Code) return
        when (val input = parseCodeInput(raw)) {
            is CodeInput.Link ->
                _state.update { it.copy(code = FieldInput(), linkToken = input.token, step = ResetStep.Password, submit = SubmitStatus.Idle) }
            is CodeInput.Digits -> {
                // A letter typed, or a seventh digit: nothing changed, and an error stays put.
                if (input.value == current.code.value) return
                // A new digit clears the old verdict and any failure above the action.
                _state.update { it.copy(code = FieldInput(value = input.value), linkToken = null, submit = SubmitStatus.Idle) }
                if (input.value.length == EMAIL_CODE_LENGTH) checkCode()
            }
        }
    }

    /**
     * Step one. Nothing is sent for a blank or short code, or for one the server already
     * refused: each wrong try spends one of its five, so the same six digits are not sent
     * twice.
     */
    private fun checkCode() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending || current.step != ResetStep.Code) return
        val digits = current.code.value
        if (digits.length == EMAIL_CODE_LENGTH && current.code.error != null) return
        val codeError = when {
            digits.isEmpty() -> Res.string.code_error_blank
            digits.length < EMAIL_CODE_LENGTH -> Res.string.code_error_short
            // Opened by a link on a phone that does not know the address: a code names no account.
            current.email.isEmpty() -> Res.string.code_error_no_address
            else -> null
        }
        if (codeError != null) {
            _state.update { it.copy(code = it.code.copy(showErrors = true, error = UiText.Resource(codeError))) }
            return
        }
        val proof = EmailProof.Code(email = current.email, code = digits)
        _state.update { it.copy(submit = SubmitStatus.Sending) }
        viewModelScope.launch {
            when (val result = authRepository.checkResetCode(current.email, digits)) {
                is Result.Success -> _state.update { it.copy(step = ResetStep.Password, submit = SubmitStatus.Idle) }
                is Result.Failure -> {
                    val proofError = AuthErrorMapper.resetProofError(result.error, proof)
                    _state.update {
                        if (proofError != null) {
                            it.copy(code = it.code.copy(showErrors = true, error = proofError), submit = SubmitStatus.Idle)
                        } else {
                            it.copy(submit = AuthErrorMapper.proofFormFailure(result.error, proof))
                        }
                    }
                }
            }
        }
    }

    /**
     * Step two: reset, then sign in with the new password. The reset answers 200 with no
     * session and revokes every session there was (doc 06 §3.1), so "Save and
     * sign in" is two requests behind one Sending label.
     *
     * Nothing typed is cleared on a failure. A refused password spends nothing
     * (doc 06 §3.1), so the same code can be sent again with a different password.
     */
    private fun submit() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending || current.step != ResetStep.Password) return
        val checked = current.copy(password = current.password.validated(AuthValidation::newPassword))
        if (checked.password.error != null) {
            _state.update { checked }
            return
        }
        val proof = current.linkToken?.let(EmailProof::Link)
            ?: EmailProof.Code(email = current.email, code = current.code.value)
        _state.update { checked.copy(submit = SubmitStatus.Sending) }
        val password = checked.password.value
        viewModelScope.launch {
            when (val reset = authRepository.resetPassword(proof, password)) {
                is Result.Success -> signIn(current.email, password)
                is Result.Failure -> onResetRefused(reset.error, proof, password)
            }
        }
    }

    private suspend fun signIn(email: String, password: String) {
        if (email.isEmpty()) {
            // Opened by the link with no address known: saved, and sign in asks for it.
            _state.update { it.copy(submit = SubmitStatus.Idle) }
            _events.send(ResetPasswordEvent.NavigateToLogin(email))
            return
        }
        when (authRepository.login(email, password)) {
            is Result.Success -> {
                _state.update { it.copy(submit = SubmitStatus.Idle) }
                _events.send(ResetPasswordEvent.NavigateToHome)
            }
            // The password is saved either way; sign in says what is wrong, with a fresh form.
            is Result.Failure -> {
                _state.update { it.copy(submit = SubmitStatus.Idle) }
                _events.send(ResetPasswordEvent.NavigateToLogin(email))
            }
        }
    }

    /**
     * A refused code or link sends the screen back to the code step with the error on the
     * field (the code passed the check but expired, or a newer email replaced it, since).
     * A refused link is dropped, so the field takes the code from the same email.
     */
    private fun onResetRefused(error: DataError, proof: EmailProof, submittedPassword: String) {
        val proofError = AuthErrorMapper.resetProofError(error, proof)
        val passwordError = AuthErrorMapper.resetPasswordError(error, submittedPassword)
        _state.update {
            when {
                proofError != null -> it.copy(
                    step = ResetStep.Code,
                    code = it.code.copy(showErrors = true, error = proofError),
                    linkToken = null,
                    password = it.password.withServerError(passwordError),
                    submit = SubmitStatus.Idle,
                )
                passwordError != null ->
                    it.copy(password = it.password.withServerError(passwordError), submit = SubmitStatus.Idle)
                else -> it.copy(submit = AuthErrorMapper.proofFormFailure(error, proof))
            }
        }
    }
}
