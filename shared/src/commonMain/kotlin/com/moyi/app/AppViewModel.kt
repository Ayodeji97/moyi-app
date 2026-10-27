package com.moyi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The app shell's state: whether someone is signed in, and the session
 * events the shell must route on.
 *
 * Restores the stored session once at launch, and again each time the app
 * returns to the foreground while the state is still [SessionState.Unknown]
 * (an iOS background launch before first unlock cannot read the Keychain, so
 * the first restore may decide nothing). A restore that throws keeps Unknown:
 * the shell shows the blank surface, never sign in, because an unreadable
 * store is not the same as no session.
 */
class AppViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    val session: StateFlow<SessionState> = authRepository.session

    /** One collector: the shell (SessionManager's events are a Channel). */
    val sessionEvents: Flow<SessionEvent> = authRepository.sessionEvents

    private var restoring: Job? = null

    init {
        restore(retry = false)
    }

    /** The app came to the foreground (ON_START). */
    fun onForeground() {
        if (session.value == SessionState.Unknown) restore(retry = true)
    }

    private fun restore(retry: Boolean) {
        if (restoring?.isActive == true) return
        restoring = viewModelScope.launch {
            try {
                restoreSession(retry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // e.g. the token store is locked (TokenStoreUnavailableException
                // after PR #4's review fixes). Stay Unknown; the next foreground retries.
                Logger.withTag("App").w { "Session restore deferred: ${e::class.simpleName}" }
            }
        }
    }

    /**
     * The one seam for restoring. [retry] is a foreground retry; once
     * SessionManager gains `retryRestore()` (PR #4 review fixes), that branch
     * calls it through the repository. Until then both call restoreSession().
     */
    private suspend fun restoreSession(retry: Boolean) {
        if (retry) authRepository.restoreSession() else authRepository.restoreSession()
    }
}
