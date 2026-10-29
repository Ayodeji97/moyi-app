package com.moyi.app.feature.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.feature.auth.common.AuthErrorMapper
import com.moyi.app.feature.auth.common.AuthValidation
import com.moyi.app.feature.auth.common.DeviceLocale
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.LegalLinks
import com.moyi.app.feature.auth.common.RegisterField
import com.moyi.app.feature.auth.common.SubmitStatus
import com.moyi.app.designsystem.presentation.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val legalLinks: LegalLinks,
    private val deviceLocale: DeviceLocale,
) : ViewModel() {
    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    private val _events = Channel<RegisterEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: RegisterAction) {
        when (action) {
            is RegisterAction.OnNameChange -> _state.update { it.copy(name = it.name.edited(action.value, AuthValidation::name)) }
            is RegisterAction.OnEmailChange -> _state.update { it.copy(email = it.email.edited(action.value, AuthValidation::email)) }
            is RegisterAction.OnPasswordChange ->
                _state.update { it.copy(password = it.password.edited(action.value, AuthValidation::newPassword)) }
            is RegisterAction.OnConsentChange -> _state.update { it.copy(consent = action.checked) }
            is RegisterAction.OnFieldBlur -> onBlur(action.field)
            RegisterAction.OnSubmit -> submit()
            RegisterAction.OnHaveAccountClick -> send(RegisterEvent.NavigateToLogin)
            RegisterAction.OnBackClick -> send(RegisterEvent.NavigateBack)
            // No published page yet means no URL: the link then does nothing
            // (owed with the site's legal pages, docs/missing-components.md).
            is RegisterAction.OnLegalLinkClick ->
                legalLinks.urlFor(action.document)?.let { send(RegisterEvent.OpenLegalPage(it)) }
        }
    }

    private fun onBlur(field: RegisterField) {
        _state.update {
            when (field) {
                RegisterField.Name -> it.copy(name = it.name.blurred(AuthValidation::name))
                RegisterField.Email -> it.copy(email = it.email.blurred(AuthValidation::email))
                RegisterField.Password -> it.copy(password = it.password.blurred(AuthValidation::newPassword))
            }
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        val checked = current.copy(
            name = current.name.validated(AuthValidation::name),
            email = current.email.validated(AuthValidation::email),
            password = current.password.validated(AuthValidation::newPassword),
        )
        if (checked.name.error != null || checked.email.error != null || checked.password.error != null) {
            _state.update { checked.copy(submit = SubmitStatus.Idle) }
            return
        }

        _state.update { checked.copy(submit = SubmitStatus.Sending) }
        val email = checked.email.value.trim()
        viewModelScope.launch {
            val result = authRepository.register(
                displayName = checked.name.value.trim(),
                email = email,
                password = checked.password.value,
                locale = deviceLocale.languageTag(),
                acceptedTermsVersion = ACCEPTED_TERMS_VERSION,
                over18 = checked.consent,
            )
            when (result) {
                is Result.Success -> {
                    _state.update { it.copy(submit = SubmitStatus.Idle) }
                    _events.send(RegisterEvent.NavigateToVerify(email))
                }
                is Result.Failure -> {
                    val fieldErrors = AuthErrorMapper.registerFieldErrors(result.error, submittedPassword = checked.password.value)
                    if (fieldErrors.isEmpty()) {
                        _state.update { it.copy(submit = AuthErrorMapper.formFailure(result.error)) }
                    } else {
                        _state.update {
                            it.copy(
                                name = it.name.withServerError(fieldErrors[RegisterField.Name]),
                                email = it.email.withServerError(fieldErrors[RegisterField.Email]),
                                password = it.password.withServerError(fieldErrors[RegisterField.Password]),
                                submit = SubmitStatus.Idle,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun send(event: RegisterEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    companion object {
        /** The Terms version the person consents to; the same constant Bruno sends. */
        const val ACCEPTED_TERMS_VERSION = "2026-09-01"
    }
}

/** A keystroke. Once errors are showing, the message tracks the value, so a fix clears it at once. */
internal fun FieldInput.edited(value: String, validate: (String) -> UiText?): FieldInput =
    copy(value = value, error = if (showErrors) validate(value) else null)

/** Left the field. Nothing typed yet is not an error, so an untouched field stays quiet. */
internal fun FieldInput.blurred(validate: (String) -> UiText?): FieldInput =
    if (value.isEmpty() && !showErrors) this else copy(showErrors = true, error = validate(value))

/** Submit pressed: every field shows its errors from now on. */
internal fun FieldInput.validated(validate: (String) -> UiText?): FieldInput =
    copy(showErrors = true, error = validate(value))

/** The server's verdict for this field, kept if present, otherwise the client's. */
internal fun FieldInput.withServerError(serverError: UiText?): FieldInput =
    if (serverError == null) this else copy(showErrors = true, error = serverError)
