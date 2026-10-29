package com.moyi.app.feature.auth.register

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.auth.common.FieldInput
import com.moyi.app.feature.auth.common.LegalDocument
import com.moyi.app.feature.auth.common.RegisterField
import com.moyi.app.feature.auth.common.SubmitStatus

/** Sign up (Onboarding 2, Figma 340:9): name, email, password, consent. */
@Immutable
data class RegisterState(
    val name: FieldInput = FieldInput(),
    val email: FieldInput = FieldInput(),
    val password: FieldInput = FieldInput(),
    val consent: Boolean = false,
    val submit: SubmitStatus = SubmitStatus.Idle,
) {
    /** Figma draws the action Disabled until consent is given (auth-placement-spec D4). */
    val canSubmit: Boolean get() = consent && submit != SubmitStatus.Sending
}

sealed interface RegisterAction {
    data class OnNameChange(val value: String) : RegisterAction
    data class OnEmailChange(val value: String) : RegisterAction
    data class OnPasswordChange(val value: String) : RegisterAction
    data class OnConsentChange(val checked: Boolean) : RegisterAction

    /** The field lost focus: its client-side error may show from now on. */
    data class OnFieldBlur(val field: RegisterField) : RegisterAction
    data object OnSubmit : RegisterAction
    data object OnHaveAccountClick : RegisterAction
    data object OnBackClick : RegisterAction

    /** Terms or Privacy Policy: opens in the in-app browser when its URL is configured. */
    data class OnLegalLinkClick(val document: LegalDocument) : RegisterAction
}

sealed interface RegisterEvent {
    /** 201: the account exists and the email is on its way. */
    data class NavigateToVerify(val email: String) : RegisterEvent
    data object NavigateToLogin : RegisterEvent
    data object NavigateBack : RegisterEvent

    /** Open a legal page in the in-app browser (D8). */
    data class OpenLegalPage(val url: String) : RegisterEvent
}
