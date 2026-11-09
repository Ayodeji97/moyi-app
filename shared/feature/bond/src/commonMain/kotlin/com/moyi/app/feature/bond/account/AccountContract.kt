package com.moyi.app.feature.bond.account

import androidx.compose.runtime.Immutable
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.account.DeletionRequest

/** Whether the account's state is on screen yet. Loading draws the bare `surface`: no spinner. */
enum class AccountLoad { Loading, Failed, Ready }

/** The deletion call in flight: one at a time, so a double tap sends one request. */
enum class DeletionCall { Request, Cancel }

/**
 * Account (Figma `Account 7 · settings`, PROPOSED rows) and its Delete your account screen:
 * one state, because the delete screen changes what the root shows.
 *
 * [password] is what the person typed to confirm. It lives here only while the delete screen
 * is open, is never saved (no SavedStateHandle: a password must not outlive the screen), and
 * is left out of [toString] so no log line can carry it.
 */
@Immutable
data class AccountState(
    val load: AccountLoad = AccountLoad.Loading,
    /** The deletion waiting to be carried out, from `GET /me`; null when none is. */
    val pending: DeletionRequest? = null,
    val password: String = "",
    val passwordError: UiText? = null,
    /** An error that is not the password's, above the delete screen's action. */
    val error: UiText? = null,
    val inFlight: DeletionCall? = null,
) {
    override fun toString(): String =
        "AccountState(load=$load, pending=$pending, password=<${password.length} chars>, passwordError=$passwordError, " +
            "error=$error, inFlight=$inFlight)"
}

sealed interface AccountAction {
    data object OnRetryClick : AccountAction
    data object OnExportClick : AccountAction
    data object OnDeleteClick : AccountAction
    data class OnPasswordChange(val value: String) : AccountAction

    /** Delete my account: the `POST`, with the password. */
    data object OnConfirmDeletion : AccountAction

    /** Keep my account: the `DELETE`, while the deletion can still be called off. */
    data object OnKeepAccount : AccountAction

    /** The delete screen closed (back, Cancel): the password and its error go with it. */
    data object OnDeleteClosed : AccountAction
}

sealed interface AccountEvent {
    data object OpenExport : AccountEvent
    data object OpenDelete : AccountEvent

    /** The deletion was called off: back to Account, which shows the account as it is. */
    data object DeletionCancelled : AccountEvent
}
