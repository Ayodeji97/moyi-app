package com.moyi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.AuthRepository
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.legal.LegalRepository
import com.moyi.app.domain.notification.PushTokenRegistration
import com.moyi.app.domain.today.OutboxSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *
 * It also wakes the outbox (doc 13 §5): its connectivity watch starts with the shell, and
 * every foreground tries the queued entries again, whatever screen is showing. The push
 * token's registration starts with it too, and a failed one is tried again on foreground.
 *
 * And it asks whether the person's consent to the Terms and the Privacy Policy is older
 * than the documents in force (ADR-0056), once Today is showing, never before: the cached
 * day opens straight to Today (owner ruling 2026-11-08), so a check that cannot be made
 * offline holds nothing up. It is decided once per signed-in person per launch; a check
 * that failed is tried again on the next foreground while Today shows.
 */
class AppViewModel(
    private val authRepository: AuthRepository,
    private val outbox: OutboxSender,
    /** Tells the server this phone's push token while someone is signed in (none until Firebase). */
    private val pushTokens: PushTokenRegistration,
    private val legalRepository: LegalRepository,
) : ViewModel() {
    val session: StateFlow<SessionState> = authRepository.session

    /** One collector: the shell (SessionManager's events are a Channel). */
    val sessionEvents: Flow<SessionEvent> = authRepository.sessionEvents

    private var restoring: Job? = null

    private val _legalConsentDue = MutableStateFlow(false)

    /** True while the re-consent step should open over Today; the shell clears it with [onLegalConsentShown]. */
    val legalConsentDue: StateFlow<Boolean> = _legalConsentDue.asStateFlow()

    private var todayShowing = false
    private var legalDecidedFor: String? = null
    private var legalCheck: Job? = null

    init {
        restore()
        outbox.start()
        pushTokens.start()
        viewModelScope.launch {
            // A new person (or none) starts the question afresh: their consent is their own.
            session.collect { state ->
                val userId = (state as? SessionState.SignedIn)?.user?.id
                if (userId != legalDecidedFor) {
                    legalDecidedFor = null
                    _legalConsentDue.value = false
                }
            }
        }
    }

    /** The app came to the foreground (ON_START). */
    fun onForeground() {
        if (session.value == SessionState.Unknown) restore()
        outbox.retrySoon()
        pushTokens.retry()
        if (todayShowing) checkLegalConsent()
    }

    /** Today is (or stops being) the screen showing, with its day in. */
    fun onTodayShowing(showing: Boolean) {
        todayShowing = showing
        if (showing) checkLegalConsent()
    }

    /** The shell opened the re-consent step. Not now records nothing; the next launch asks again. */
    fun onLegalConsentShown() {
        _legalConsentDue.value = false
    }

    private fun checkLegalConsent() {
        val userId = (session.value as? SessionState.SignedIn)?.user?.id ?: return
        if (legalDecidedFor == userId || legalCheck?.isActive == true) return
        legalCheck = viewModelScope.launch {
            when (val result = legalRepository.status()) {
                is Result.Success -> {
                    legalDecidedFor = userId
                    if (!result.data.upToDate) _legalConsentDue.value = true
                }
                // Offline or the server in trouble: Today stays as it is, and the next
                // foreground asks again. Nothing is shown for a check that could not run.
                is Result.Failure -> Unit
            }
        }
    }

    /**
     * restoreSession() is idempotent: a decided state returns at once, and
     * Unknown tries the store again. So the foreground retry is the same call.
     */
    private fun restore() {
        if (restoring?.isActive == true) return
        restoring = viewModelScope.launch {
            try {
                authRepository.restoreSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // e.g. the token store is locked (TokenStoreUnavailableException).
                // Stay Unknown, never signed out; the next foreground retries.
                Logger.withTag("App").w { "Session restore deferred: ${e::class.simpleName}" }
            }
        }
    }
}
