package com.moyi.app.feature.auth.common

import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.feature.auth.resources.Res
import com.moyi.app.feature.auth.resources.auth_email_error
import com.moyi.app.feature.auth.resources.login_password_error_blank
import com.moyi.app.feature.auth.resources.register_name_error_blank
import com.moyi.app.feature.auth.resources.register_name_error_long
import com.moyi.app.feature.auth.resources.register_password_error_short

/**
 * Light client-side checks. The server is the authority (it also checks the
 * breach list and the real password rules); these only save a round trip for
 * the obvious cases, and use the same messages the server's codes map to.
 */
object AuthValidation {
    /** The API's `displayName` limit (RegisterRequest maxLength 80). */
    const val NAME_MAX_LENGTH = 80

    /** FR-001: 8 characters or more, nothing else. */
    const val PASSWORD_MIN_LENGTH = 8

    // Something, an @, something, a dot, something. Deliberately loose: the
    // server decides what an address is; this only catches a missing @.
    private val emailShape = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun name(value: String): UiText? = when {
        value.isBlank() -> UiText.Resource(Res.string.register_name_error_blank)
        value.trim().length > NAME_MAX_LENGTH -> UiText.Resource(Res.string.register_name_error_long)
        else -> null
    }

    fun email(value: String): UiText? =
        if (emailShape.matches(value.trim())) null else UiText.Resource(Res.string.auth_email_error)

    /** Counts characters as the server does (length of the string); spaces count, nothing is trimmed. */
    fun newPassword(value: String): UiText? =
        if (value.length >= PASSWORD_MIN_LENGTH) null else UiText.Resource(Res.string.register_password_error_short)

    fun existingPassword(value: String): UiText? =
        if (value.isEmpty()) UiText.Resource(Res.string.login_password_error_blank) else null
}
