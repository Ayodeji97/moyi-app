package com.moyi.app.feature.auth.reset

import androidx.compose.runtime.Immutable
import com.moyi.app.feature.auth.common.SubmitStatus

/**
 * Change password (Account › Change password, NEW: docs/missing-components.md §83): the start
 * of the reset for someone signed in. Nothing to type: the code goes to the signed-in address.
 *
 * [email] is that address, from the session kept on this device; null when no one is signed
 * in (the screen is only reachable signed in, so in practice never), and then nothing can be
 * sent. It is personal data, so [toString] leaves it out.
 */
@Immutable
data class ChangePasswordState(
    val email: String? = null,
    val submit: SubmitStatus = SubmitStatus.Idle,
) {
    override fun toString(): String =
        "ChangePasswordState(email=${if (email == null) "null" else "<redacted>"}, submit=$submit)"
}

sealed interface ChangePasswordAction {
    /** "Send the code". */
    data object OnSubmit : ChangePasswordAction
    data object OnBackClick : ChangePasswordAction
}

sealed interface ChangePasswordEvent {
    /** The code is on its way: the code step of the reset, signed in, for [email]. */
    data class NavigateToCode(val email: String) : ChangePasswordEvent

    /** Back to Account. */
    data object NavigateBack : ChangePasswordEvent
}
