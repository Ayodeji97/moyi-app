package com.moyi.app.feature.onboarding.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.feature.onboarding.common.formMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Signed in, not verified (FR-002). The gate has no "check again" control in
 * Figma; it re-reads `GET /me` each time the app returns to the foreground,
 * which is when someone who opened the link in their mail app comes back
 * (states.md §10: show the correct state on foreground refresh).
 *
 * "Send it again" asks for a new link and turns into the acknowledgement for
 * a minute, the server's own cooldown (ADR-0023).
 */
class UnverifiedGateViewModel(
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val resendCooldown: Duration = 1.minutes,
) : ViewModel() {
    private val _state = MutableStateFlow(
        UnverifiedGateState(email = (auth.session.value as? SessionState.SignedIn)?.user?.email.orEmpty()),
    )
    val state: StateFlow<UnverifiedGateState> = _state.asStateFlow()

    private val _events = Channel<UnverifiedGateEvent>()
    val events = _events.receiveAsFlow()

    private var checking: Job? = null

    fun onAction(action: UnverifiedGateAction) {
        when (action) {
            UnverifiedGateAction.OnResendClick -> resend()
            UnverifiedGateAction.OnResume -> recheck()
        }
    }

    private fun recheck() {
        if (checking?.isActive == true) return
        checking = viewModelScope.launch {
            // A failed check changes nothing on screen: the gate is still true until shown otherwise.
            val user = account.currentUser()
            if (user is Result.Success) {
                if (user.data.emailVerified) {
                    _events.send(UnverifiedGateEvent.NavigateToOnboarding)
                } else if (_state.value.email.isEmpty()) {
                    _state.update { it.copy(email = user.data.email) }
                }
            }
        }
    }

    private fun resend() {
        val current = _state.value
        if (current.resend != GateResendStatus.Ready || current.email.isEmpty()) return
        _state.update { it.copy(resend = GateResendStatus.Sending, resendFailure = null) }
        viewModelScope.launch {
            when (val result = auth.resendVerification(current.email)) {
                is Result.Success -> {
                    _state.update { it.copy(resend = GateResendStatus.Sent) }
                    delay(resendCooldown)
                    _state.update { it.copy(resend = GateResendStatus.Ready) }
                }
                is Result.Failure -> _state.update {
                    it.copy(resend = GateResendStatus.Ready, resendFailure = result.error.formMessage())
                }
            }
        }
    }
}
