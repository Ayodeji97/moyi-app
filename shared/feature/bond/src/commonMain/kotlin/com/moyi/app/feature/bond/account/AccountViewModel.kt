package com.moyi.app.feature.bond.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.designsystem.presentation.UiText
import com.moyi.app.domain.account.AccountDeletionRepository
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.today.EntryOutbox
import com.moyi.app.domain.today.OutboxSender
import com.moyi.app.feature.bond.resources.Res
import com.moyi.app.feature.bond.resources.delete_account_error_blank
import com.moyi.app.feature.bond.resources.delete_account_error_password
import com.moyi.app.feature.bond.resources.delete_account_error_rate_limited
import com.moyi.app.feature.bond.resources.delete_account_error_too_late
import com.moyi.app.feature.bond.resources.settings_error_generic
import com.moyi.app.feature.bond.signout.SignOut
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Account and Delete your account (FR-008, ADR-0058), one ViewModel scoped to the Account
 * entry, so the delete screen pushed over it sees the same state.
 *
 * - **Load**: `GET /me`'s `deletionRequest`, so a pending deletion shows after a restart or
 *   a reinstall. A failure is the Generic error with Try again.
 * - **Delete** asks for the password again (the backend's rule: a session is not proof
 *   enough). Blank is refused here, before any request. Then `POST /me/deletion-request`;
 *   the answer is the pending deletion, and the screen turns to it. A wrong password is a
 *   `403 INVALID_CREDENTIALS`, shown on the field; the session is fine and the authed client
 *   neither refreshes nor signs out on it (docs/backend-contract-gaps.md, row 33).
 * - **Keep my account** is `DELETE /me/deletion-request`. A `404` means nothing can be called
 *   off any more: reload, and say it is too late if a deletion is still there.
 * - One call at a time: a second tap while one is in flight does nothing.
 * - **Sign out** (owner ruling 2026-11-09) runs Bond settings' sequence ([SignOut]): one more
 *   try to send what is waiting, then ask, or sign out. Account is the one place a person
 *   with no open bond can sign out. A second tap while it runs does nothing.
 */
class AccountViewModel(
    private val deletion: AccountDeletionRepository,
    auth: AuthRepository,
    /** Entries sent while offline: a sign-out tries them once more before the session wipe takes them. */
    outbox: EntryOutbox,
    sender: OutboxSender,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The same sign-out as Bond settings' (owner ruling 2026-11-09). */
    private val signOutSequence = SignOut(auth, outbox, sender)

    init {
        load()
    }

    fun onAction(action: AccountAction) {
        when (action) {
            AccountAction.OnRetryClick -> load()
            AccountAction.OnExportClick -> if (!_state.value.signingOut) send(AccountEvent.OpenExport)
            AccountAction.OnDeleteClick -> if (!_state.value.signingOut) {
                _state.update { it.copy(password = "", passwordError = null, error = null) }
                send(AccountEvent.OpenDelete)
            }
            is AccountAction.OnPasswordChange -> _state.update { it.copy(password = action.value, passwordError = null) }
            AccountAction.OnConfirmDeletion -> requestDeletion()
            AccountAction.OnKeepAccount -> cancelDeletion()
            AccountAction.OnDeleteClosed -> _state.update { it.copy(password = "", passwordError = null, error = null) }
            AccountAction.OnSignOutClick -> signOut()
            AccountAction.OnSignOutUnsentConfirm -> {
                _state.update { it.copy(confirmingUnsentSignOut = false, signingOut = true) }
                viewModelScope.launch { signOutSequence.anyway() }
            }
            AccountAction.OnSignOutUnsentDismiss -> _state.update { it.copy(confirmingUnsentSignOut = false) }
        }
    }

    private fun signOut() {
        if (_state.value.signingOut) return
        _state.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            if (!signOutSequence.unlessWordsWouldBeLost()) {
                _state.update { it.copy(signingOut = false, confirmingUnsentSignOut = true) }
            }
        }
    }

    private fun load() {
        _state.update { it.copy(load = AccountLoad.Loading) }
        viewModelScope.launch {
            when (val result = deletion.pending()) {
                is Result.Success -> _state.update { it.copy(load = AccountLoad.Ready, pending = result.data) }
                is Result.Failure -> _state.update { it.copy(load = AccountLoad.Failed) }
            }
        }
    }

    private fun requestDeletion() {
        val current = _state.value
        if (current.inFlight != null || current.pending != null) return
        if (current.password.isBlank()) {
            _state.update { it.copy(passwordError = UiText.Resource(Res.string.delete_account_error_blank)) }
            return
        }
        _state.update { it.copy(inFlight = DeletionCall.Request, passwordError = null, error = null) }
        viewModelScope.launch {
            when (val result = deletion.request(current.password)) {
                // The password is done with: dropped from memory the moment it has been used.
                is Result.Success -> _state.update { it.copy(inFlight = null, pending = result.data, password = "") }
                is Result.Failure -> _state.update { failed ->
                    when {
                        result.error.isWrongPassword() -> failed.copy(inFlight = null, passwordError = wrongPassword)
                        result.error.code() == ApiErrorCode.VALIDATION_FAILED -> failed.copy(inFlight = null, passwordError = blank)
                        result.error.code() == ApiErrorCode.RATE_LIMITED -> failed.copy(inFlight = null, error = rateLimited)
                        else -> failed.copy(inFlight = null, error = generic)
                    }
                }
            }
        }
    }

    private fun cancelDeletion() {
        val current = _state.value
        if (current.inFlight != null || current.pending == null) return
        _state.update { it.copy(inFlight = DeletionCall.Cancel, error = null) }
        viewModelScope.launch {
            when (val result = deletion.cancel()) {
                is Result.Success -> {
                    _state.update { it.copy(inFlight = null, pending = null) }
                    _events.send(AccountEvent.DeletionCancelled)
                }
                is Result.Failure -> if (result.error.status() == NOT_FOUND) afterNothingToCancel() else {
                    _state.update { it.copy(inFlight = null, error = generic) }
                }
            }
        }
    }

    /** `404`: already called off elsewhere, or past the window. `/me` says which. */
    private suspend fun afterNothingToCancel() {
        when (val result = deletion.pending()) {
            is Result.Success -> if (result.data == null) {
                _state.update { it.copy(inFlight = null, pending = null) }
                _events.send(AccountEvent.DeletionCancelled)
            } else {
                _state.update { it.copy(inFlight = null, pending = result.data, error = tooLate) }
            }
            is Result.Failure -> _state.update { it.copy(inFlight = null, error = generic) }
        }
    }

    private fun send(event: AccountEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun DataError.Network.code(): ApiErrorCode? = (this as? DataError.Network.Api)?.problem?.code

    private fun DataError.Network.status(): Int? = (this as? DataError.Network.Api)?.problem?.status

    /** Matched by code: a wrong password is `403 INVALID_CREDENTIALS`; a refused token (`401`) never reaches here. */
    private fun DataError.Network.isWrongPassword(): Boolean = code() == ApiErrorCode.INVALID_CREDENTIALS

    private val wrongPassword = UiText.Resource(Res.string.delete_account_error_password)
    private val blank = UiText.Resource(Res.string.delete_account_error_blank)
    private val rateLimited = UiText.Resource(Res.string.delete_account_error_rate_limited)
    private val tooLate = UiText.Resource(Res.string.delete_account_error_too_late)
    private val generic = UiText.Resource(Res.string.settings_error_generic)
}

private const val NOT_FOUND = 404
