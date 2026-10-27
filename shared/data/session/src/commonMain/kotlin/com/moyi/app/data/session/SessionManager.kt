package com.moyi.app.data.session

import co.touchlab.kermit.Logger
import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.api.models.RefreshRequest
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.BearerTokenSource
import com.moyi.app.data.network.RefreshUnavailableException
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.domain.auth.SessionEvent
import com.moyi.app.domain.auth.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one owner of the session on this device.
 *
 * - The **access token** lives here, in memory only (CLAUDE.md).
 * - The **refresh token** lives in [store] and is read only here.
 * - **One refresh at a time** ([refresh]). Refresh tokens rotate strictly and
 *   a token presented twice ends every session (ADR-0021), so two concurrent
 *   refreshes are not a performance problem, they are a sign-out.
 *
 * It refreshes through [refreshApi], which must sit on the PUBLIC client: a
 * refresh that itself went through the Auth plugin could recurse on its own 401.
 *
 * [events] is a [Channel], not a SharedFlow: an [SessionEvent.Ended] that
 * fires while no screen is collecting (mid-navigation, app in background) is
 * buffered until one is, and is then delivered exactly once. A SharedFlow
 * without replay would drop it; with replay it would deliver it again to
 * every new collector. There is one collector (the app's root), which a
 * Channel assumes. The lasting fact, signed in or out, is [sessionState].
 */
class SessionManager(
    private val store: SecureTokenStore,
    private val refreshApi: RefreshControllerApi,
) : BearerTokenSource {
    private val mutex = Mutex()
    private val accessToken = MutableStateFlow<String?>(null)
    private val state = MutableStateFlow<SessionState>(SessionState.Unknown)
    private val eventChannel = Channel<SessionEvent>(Channel.BUFFERED)
    private val log = Logger.withTag("Session")

    val sessionState: StateFlow<SessionState> = state.asStateFlow()
    val events: Flow<SessionEvent> = eventChannel.receiveAsFlow()

    override fun currentAccessToken(): String? = accessToken.value

    /**
     * Returns a usable access token after [staleAccess] was refused, or null.
     *
     * Single-flight: callers queue on a [Mutex]. Once inside, if the current
     * token is no longer [staleAccess], someone ahead in the queue already
     * refreshed, and that token is returned without a network call.
     * Otherwise one `POST /auth/refresh` is made with the stored refresh
     * token, and on success the new refresh token is **written to the store
     * before** the new access token is published, so nothing can use the new
     * access token while the only copy of the new refresh token is in memory.
     *
     * - `401 TOKEN_REUSE_DETECTED` or `REFRESH_TOKEN_INVALID`: the session is
     *   over. Store and memory are wiped and [SessionEvent.Ended] is emitted.
     * - Offline, timeout, 5xx or anything else: nothing changes and null is
     *   returned. A bad network never signs anyone out.
     */
    suspend fun refresh(staleAccess: String?): String? =
        (refreshOutcome(staleAccess) as? RefreshOutcome.Refreshed)?.accessToken

    /** [refresh] for the authed client, which also needs to know WHY there is no token (see [BearerTokenSource]). */
    override suspend fun refreshAccessToken(staleAccessToken: String?): String? =
        when (val outcome = refreshOutcome(staleAccessToken)) {
            is RefreshOutcome.Refreshed -> outcome.accessToken
            RefreshOutcome.NoSession, is RefreshOutcome.Ended -> null
            is RefreshOutcome.Unavailable -> throw RefreshUnavailableException(outcome.error)
        }

    /**
     * After a successful login: the refresh token is persisted first, then the
     * access token and user are published.
     *
     * @throws Exception if the store could not write; nothing is published then.
     */
    suspend fun startSession(grant: SessionGrant) {
        mutex.withLock {
            withContext(NonCancellable) {
                store.write(grant.toStoredSession())
                accessToken.value = grant.accessToken
                state.value = SessionState.SignedIn(grant.user)
            }
        }
    }

    /** Wipes the session on this device and emits [SessionEvent.Ended]. Does not call the server. */
    suspend fun endSession(reason: SessionEndReason) {
        mutex.withLock { withContext(NonCancellable) { endLocked(reason) } }
    }

    /** The stored refresh token, for `POST /auth/logout`. Null when signed out. */
    suspend fun storedRefreshToken(): String? = store.read()?.refreshToken

    /**
     * Cold start. With a stored session, one refresh: success is
     * [SessionState.SignedIn]; a session the server ended is
     * [SessionState.SignedOut]; no network keeps the person signed in with the
     * stored user, and the first authed request refreshes again. With no
     * stored session it is [SessionState.SignedOut] and nothing is sent.
     */
    suspend fun restore(): SessionState {
        val next = when (val outcome = refreshOutcome(staleAccess = null)) {
            is RefreshOutcome.Refreshed -> state.value
            RefreshOutcome.NoSession, is RefreshOutcome.Ended -> SessionState.SignedOut
            is RefreshOutcome.Unavailable -> SessionState.SignedIn(outcome.stored.toUser())
        }
        // A concurrent refresh, login or sign-out may have moved the state on;
        // only an undecided launch state is replaced here.
        state.compareAndSet(SessionState.Unknown, next)
        return state.value
    }

    private suspend fun refreshOutcome(staleAccess: String?): RefreshOutcome = mutex.withLock {
        val current = accessToken.value
        if (current != null && current != staleAccess) return@withLock RefreshOutcome.Refreshed(current)

        val stored = store.read() ?: return@withLock RefreshOutcome.NoSession

        // From here the server may rotate the token. The caller going away
        // must not abandon the answer: a rotated token that is never stored
        // makes the next refresh a reuse. HttpTimeout bounds the wait.
        withContext(NonCancellable) {
            val result = safeApiCall { refreshApi.refresh(RefreshRequest(stored.refreshToken)) }
            val grant = when (result) {
                is Result.Success -> result.data.toSessionGrant()
                is Result.Failure -> result
            }
            when (grant) {
                is Result.Success -> publish(grant.data)
                is Result.Failure -> onRefreshFailed(grant.error, stored)
            }
        }
    }

    /** Store first, then memory. Called with the lock held. */
    private suspend fun publish(grant: SessionGrant): RefreshOutcome {
        try {
            store.write(grant.toStoredSession())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The server has rotated and the new refresh token could not be
            // kept. Presenting the old one again would be a reuse that ends
            // every session, so end only this one, here.
            log.w { "Rotated refresh token could not be stored; ending the session" }
            endLocked(SessionEndReason.Invalid)
            return RefreshOutcome.Ended(SessionEndReason.Invalid)
        }
        accessToken.value = grant.accessToken
        state.value = SessionState.SignedIn(grant.user)
        return RefreshOutcome.Refreshed(grant.accessToken)
    }

    private suspend fun onRefreshFailed(error: DataError.Network, stored: StoredSession): RefreshOutcome {
        val reason = when ((error as? DataError.Network.Api)?.problem?.code) {
            ApiErrorCode.TOKEN_REUSE_DETECTED -> SessionEndReason.ReuseDetected
            ApiErrorCode.REFRESH_TOKEN_INVALID -> SessionEndReason.Invalid
            else -> null
        }
        if (reason == null) {
            log.i { "Refresh unavailable, session kept: $error" }
            return RefreshOutcome.Unavailable(error, stored)
        }
        endLocked(reason)
        return RefreshOutcome.Ended(reason)
    }

    /** Called with the lock held. */
    private suspend fun endLocked(reason: SessionEndReason) {
        try {
            store.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            log.w { "Stored session could not be cleared" }
        }
        accessToken.value = null
        state.value = SessionState.SignedOut
        log.i { "Session ended: $reason" }
        eventChannel.trySend(SessionEvent.Ended(reason))
    }

    private sealed interface RefreshOutcome {
        data class Refreshed(val accessToken: String) : RefreshOutcome {
            override fun toString(): String = "Refreshed(<redacted>)"
        }

        data object NoSession : RefreshOutcome

        data class Ended(val reason: SessionEndReason) : RefreshOutcome

        class Unavailable(val error: DataError.Network, val stored: StoredSession) : RefreshOutcome
    }
}
