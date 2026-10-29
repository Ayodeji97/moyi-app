package com.moyi.app.feature.onboarding.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AccountRepository
import com.moyi.app.domain.bond.BondRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Decides the first signed-in screen from the server, never from what the
 * device remembers: a bond can be joined, left or ended on the other phone.
 *
 * `GET /bonds` and `GET /me` run side by side (states.md §3: no chained round
 * trips). An open bond wins: Today, whatever the account's state. With none,
 * an unverified account goes to the gate, since it can neither create nor join
 * (FR-002), and a verified one to relationship type.
 *
 * Only the first open bond is used: there is no screen for more than one
 * (states.md §2, a recorded gap). The list is newest first (doc 06 §3.3).
 */
class SignedInStartViewModel(
    private val bonds: BondRepository,
    private val account: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SignedInStartState.Checking)
    val state: StateFlow<SignedInStartState> = _state.asStateFlow()

    private val _events = Channel<SignedInStartEvent>()
    val events = _events.receiveAsFlow()

    init {
        check()
    }

    fun onAction(action: SignedInStartAction) {
        when (action) {
            SignedInStartAction.OnRetryClick -> if (_state.value == SignedInStartState.Failed) check()
        }
    }

    private fun check() {
        _state.value = SignedInStartState.Checking
        viewModelScope.launch {
            val (bondList, user) = coroutineScope {
                val b = async { bonds.bonds() }
                val u = async { account.currentUser() }
                b.await() to u.await()
            }
            val event = when {
                bondList is Result.Success && bondList.data.any { it.isOpen } -> SignedInStartEvent.NavigateToToday
                bondList is Result.Failure || user is Result.Failure -> null
                (user as Result.Success).data.emailVerified -> SignedInStartEvent.NavigateToOnboarding
                else -> SignedInStartEvent.NavigateToGate
            }
            if (event == null) _state.value = SignedInStartState.Failed else _events.send(event)
        }
    }
}
