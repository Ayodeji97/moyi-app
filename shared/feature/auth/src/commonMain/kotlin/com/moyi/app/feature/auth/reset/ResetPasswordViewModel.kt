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
 * [linkToken] is the token from the emailed link when the link opened the app
 * (`https://getmoyi.app/reset-password?token=…`, DeepLink): it is the proof, and the person
 * only chooses the password. Nothing is sent until they do.
 *
 * Otherwise the proof is the six-digit code from the email with the address typed on Forgot
 * password ([ResetPasswordState.email]). Unlike verify, the sixth digit sends nothing: the
 * server checks a reset code only together with the new password (there is no check-only
 * call, backend-contract-gaps.md row 39), so the screen moves on to the password instead.
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

    fun onAction(action: ResetPasswordAction) {
        when (action) {
            is ResetPasswordAction.OnCodeChange -> onCodeChange(action.value)
            is ResetPasswordAction.OnPasswordChange ->
                _state.update { it.copy(password = it.password.edited(action.value, AuthValidation::newPassword)) }
            ResetPasswordAction.OnPasswordBlur ->
                _state.update { it.copy(password = it.password.blurred(AuthValidation::newPassword)) }
            ResetPasswordAction.OnSubmit -> submit()
            ResetPasswordAction.OnBackClick -> viewModelScope.launch { _events.send(ResetPasswordEvent.NavigateBack) }
        }
    }

    /**
     * Digits, six at most, or a pasted link, which becomes the proof as an opened link
     * would. Ignored while saving: the field keeps showing what was sent.
     */
    private fun onCodeChange(raw: String) {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending) return
        when (val input = parseCodeInput(raw)) {
            is CodeInput.Link -> _state.update { it.copy(code = FieldInput(), linkToken = input.token) }
            is CodeInput.Digits -> {
                // A letter typed, or a seventh digit: nothing changed, and an error stays put.
                if (input.value == current.code.value) return
                // Typing means the code is the proof now, not a link held from before.
                _state.update { it.copy(code = FieldInput(value = input.value), linkToken = null) }
            }
        }
    }

    /**
     * Reset, then sign in with the new password: the reset answers 200 with no
     * session and revokes every session there was (doc 06 §3.1), so "Save and
     * sign in" is two requests behind one Sending label.
     *
     * Nothing typed is cleared on a failure. A refused password spends nothing
     * (doc 06 §3.1), so the same code can be sent again with a different password.
     */
    private fun submit() {
        val current = _state.value
        if (current.submit == SubmitStatus.Sending) return
        val digits = current.code.value
        val codeError = when {
            current.linkToken != null -> null
            // This exact code was refused; sending it again would only spend another try.
            current.code.error != null && digits.length == EMAIL_CODE_LENGTH -> current.code.error
            digits.isEmpty() -> UiText.Resource(Res.string.code_error_blank)
            digits.length < EMAIL_CODE_LENGTH -> UiText.Resource(Res.string.code_error_short)
            // Opened by a link on a phone that does not know the address: a code names no account.
            current.email.isEmpty() -> UiText.Resource(Res.string.code_error_no_address)
            else -> null
        }
        val checked = current.copy(
            code = if (codeError == null) current.code else current.code.copy(showErrors = true, error = codeError),
            password = current.password.validated(AuthValidation::newPassword),
        )
        if (checked.code.error != null || checked.password.error != null) {
            _state.update { checked }
            return
        }
        val proof = current.linkToken?.let(EmailProof::Link) ?: EmailProof.Code(email = current.email, code = digits)
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
     * A refused link is dropped, so the field takes the code from the same email; its
     * error shows under the field either way.
     */
    private fun onResetRefused(error: DataError, proof: EmailProof, submittedPassword: String) {
        val proofError = AuthErrorMapper.resetProofError(error, proof)
        val passwordError = AuthErrorMapper.resetPasswordError(error, submittedPassword)
        _state.update {
            if (proofError == null && passwordError == null) {
                it.copy(submit = AuthErrorMapper.proofFormFailure(error, proof))
            } else {
                it.copy(
                    code = if (proofError == null) it.code else it.code.copy(showErrors = true, error = proofError),
                    linkToken = if (proofError != null && proof is EmailProof.Link) null else it.linkToken,
                    password = it.password.withServerError(passwordError),
                    submit = SubmitStatus.Idle,
                )
            }
        }
    }
}
