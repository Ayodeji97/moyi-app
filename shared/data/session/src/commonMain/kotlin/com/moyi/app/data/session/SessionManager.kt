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
    logger: Logger = Logger,
) : BearerTokenSource {
    private val mutex = Mutex()
    private val accessToken = MutableStateFlow<String?>(null)

    /**
     * Every access token issued in the current session (login, then each
     * refresh). A 401 for a token NOT in here belongs to an earlier session,
     * say the account that signed out a moment ago, and must not be retried
     * with this session's token. Reset at login and when the session ends;
     * guarded by [mutex]. One entry per refresh, so it grows by one every 15
     * minutes of use: small enough not to bound.
     */
    private val issued = mutableSetOf<String>()
    private val state = MutableStateFlow<SessionState>(SessionState.Unknown)
    private val eventChannel = Channel<SessionEvent>(Channel.BUFFERED)
    private val log = logger.withTag("Session")

    /** Only exception class names, never a token: ADR-0021's "Revisit when", made measurable. */
    private val ambiguousLog = logger.withTag("SessionRefreshAmbiguous")

    /**
     * The last refresh failure in this process that may have rotated the
     * token without our hearing (its exception class name), or null. A
     * `TOKEN_REUSE_DETECTED` while it is set is logged as correlated. Cleared
     * by a successful refresh or a new session; guarded by [mutex].
     */
    private var lastAmbiguousFailure: String? = null

    val sessionState: StateFlow<SessionState> = state.asStateFlow()
    val events: Flow<SessionEvent> = eventChannel.receiveAsFlow()

    override fun currentAccessToken(): String? = accessToken.value

    /**
     * Returns a usable access token after [staleAccess] was refused, or null.
     *
     * Single-flight: callers queue on a [Mutex]. Once inside, if the current
     * token is no longer [staleAccess], someone ahead in the queue already
     * refreshed, and that token is returned without a network call. That
     * holds only for a [staleAccess] issued in THIS session: a late 401 for a
     * token from an earlier session (A signed out, B signed in, then A's
     * request is refused) gets null, so it is never retried as B.
     * Otherwise one `POST /auth/refresh` is made with the stored refresh
     * token, and on success the new refresh token is **written to the store
     * before** the new access token is published, so nothing can use the new
     * access token while the only copy of the new refresh token is in memory.
     *
     * - `401 TOKEN_REUSE_DETECTED` or `REFRESH_TOKEN_INVALID`: the session is
     *   over. Store and memory are wiped and [SessionEvent.Ended] is emitted.
     * - Offline, timeout, 5xx or anything else: nothing changes and null is
     *   returned. The stored refresh token is kept, because there is nothing
     *   better to present next time.
     *
     * That last rule does NOT mean a bad network never signs anyone out. The
     * risk window is the whole time the refresh is in flight, not a few
     * milliseconds: once the request has left, the server may rotate R1 to R2
     * and the answer can still be lost (a read or request timeout, a reset
     * connection). Presenting R1 again then trips `TOKEN_REUSE_DETECTED`,
     * which revokes the family and emails the person (ADR-0021, no grace
     * window). No client-side fix exists under strict rotation. What the
     * client does is tell the two cases apart ([RefreshFailureKind]): a
     * failure that may have reached the server is logged under the Kermit
     * tag `SessionRefreshAmbiguous`, and so is a reuse that follows one in
     * the same process, so the rate can be measured before the backend is
     * asked for a fix (docs/backend-contract-gaps.md, row 11).
     */
    suspend fun refresh(staleAccess: String?): String? =
        (refreshOutcome(staleAccess) as? RefreshOutcome.Refreshed)?.accessToken

    /** [refresh] for the authed client, which also needs to know WHY there is no token (see [BearerTokenSource]). */
    override suspend fun refreshAccessToken(staleAccessToken: String?): String? =
        when (val outcome = refreshOutcome(staleAccessToken)) {
            is RefreshOutcome.Refreshed -> outcome.accessToken
            RefreshOutcome.NoSession, is RefreshOutcome.Ended -> null
            is RefreshOutcome.Unavailable -> throw RefreshUnavailableException(outcome.error)
            // The store could not be read (locked Keychain): fail the request,
            // not the session. DataError has no "local" network error, so Unknown.
            RefreshOutcome.StoreUnavailable -> throw RefreshUnavailableException(DataError.Network.Unknown)
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
                lastAmbiguousFailure = null
                issued.clear()
                issued += grant.accessToken
                accessToken.value = grant.accessToken
                state.value = SessionState.SignedIn(grant.user)
            }
        }
    }

    /** Wipes the session on this device and emits [SessionEvent.Ended]. Does not call the server. */
    suspend fun endSession(reason: SessionEndReason) {
        mutex.withLock { withContext(NonCancellable) { endLocked(reason) } }
    }

    /**
     * The stored refresh token, for `POST /auth/logout`. Null when signed out,
     * and null when the store cannot be read right now: sign-out must still
     * wipe this device, and the server-side family then simply expires.
     */
    suspend fun storedRefreshToken(): String? = try {
        store.read()?.refreshToken
    } catch (_: TokenStoreUnavailableException) {
        log.w { "Stored session not readable; signing out without telling the server" }
        null
    }

    /**
     * Cold start. With a stored session, one refresh: success is
     * [SessionState.SignedIn]; a session the server ended is
     * [SessionState.SignedOut]; no network keeps the person signed in with the
     * stored user, and the first authed request refreshes again. With no
     * stored session it is [SessionState.SignedOut] and nothing is sent.
     *
     * A store that cannot be read right now ([TokenStoreUnavailableException]:
     * a locked Keychain after a reboot) leaves the state [SessionState.Unknown]
     * and sends nothing. It is not signed out: the session is still there.
     *
     * Idempotent, and that is the retry: once the state is decided it is
     * returned without any work, and while it is still Unknown each call
     * tries again. The app shell calls it at launch and again whenever the
     * app returns to the foreground while the state is Unknown.
     */
    suspend fun restore(): SessionState {
        state.value.let { if (it != SessionState.Unknown) return it }
        val next = when (val outcome = refreshOutcome(staleAccess = null)) {
            is RefreshOutcome.Refreshed -> state.value
            RefreshOutcome.NoSession, is RefreshOutcome.Ended -> SessionState.SignedOut
            is RefreshOutcome.Unavailable -> SessionState.SignedIn(outcome.stored.toUser())
            RefreshOutcome.StoreUnavailable -> SessionState.Unknown
        }
        // A concurrent refresh, login or sign-out may have moved the state on;
        // only an undecided launch state is replaced here.
        state.compareAndSet(SessionState.Unknown, next)
        return state.value
    }

    private suspend fun refreshOutcome(staleAccess: String?): RefreshOutcome = mutex.withLock {
        // A token this session never issued is a previous session's request
        // arriving late: no retry as the current account, no refresh spent.
        if (staleAccess != null && staleAccess !in issued) return@withLock RefreshOutcome.NoSession

        val current = accessToken.value
        if (current != null && current != staleAccess) return@withLock RefreshOutcome.Refreshed(current)

        val stored = try {
            store.read()
        } catch (_: TokenStoreUnavailableException) {
            log.w { "Stored session not readable right now; session kept" }
            return@withLock RefreshOutcome.StoreUnavailable
        } ?: return@withLock RefreshOutcome.NoSession

        // From here the server may rotate the token. The caller going away
        // must not abandon the answer: a rotated token that is never stored
        // makes the next refresh a reuse. HttpTimeout bounds the wait.
        withContext(NonCancellable) {
            // Kept to tell a request that never left from one whose answer was
            // lost (RefreshFailureKind); safeApiCall keeps only the error type.
            var thrown: Throwable? = null
            val result = safeApiCall {
                try {
                    refreshApi.refresh(RefreshRequest(stored.refreshToken))
                } catch (e: Throwable) {
                    thrown = e
                    throw e
                }
            }
            val grant = when (result) {
                is Result.Success -> result.data.toSessionGrant()
                is Result.Failure -> result
            }
            when (grant) {
                is Result.Success -> publish(grant.data)
                is Result.Failure -> onRefreshFailed(grant.error, stored, thrown)
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
        lastAmbiguousFailure = null
        issued += grant.accessToken
        accessToken.value = grant.accessToken
        state.value = SessionState.SignedIn(grant.user)
        return RefreshOutcome.Refreshed(grant.accessToken)
    }

    private suspend fun onRefreshFailed(error: DataError.Network, stored: StoredSession, thrown: Throwable?): RefreshOutcome {
        val reason = when ((error as? DataError.Network.Api)?.problem?.code) {
            ApiErrorCode.TOKEN_REUSE_DETECTED -> SessionEndReason.ReuseDetected
            ApiErrorCode.REFRESH_TOKEN_INVALID -> SessionEndReason.Invalid
            else -> null
        }
        if (reason == null) {
            if (classifyRefreshFailure(thrown, error) == RefreshFailureKind.Ambiguous) {
                val cause = thrown?.let { it::class.simpleName } ?: error::class.simpleName
                lastAmbiguousFailure = cause
                ambiguousLog.w { "Refresh failed after it may have reached the server ($cause); the next refresh may be read as reuse" }
            }
            log.i { "Refresh unavailable, session kept: $error" }
            return RefreshOutcome.Unavailable(error, stored)
        }
        lastAmbiguousFailure?.let { cause ->
            if (reason == SessionEndReason.ReuseDetected) {
                ambiguousLog.w { "TOKEN_REUSE_DETECTED after an ambiguous refresh failure ($cause) in this process" }
            }
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
        lastAmbiguousFailure = null
        issued.clear()
        state.value = SessionState.SignedOut
        log.i { "Session ended: $reason" }
        eventChannel.trySend(SessionEvent.Ended(reason))
    }

    private sealed interface RefreshOutcome {
        data class Refreshed(val accessToken: String) : RefreshOutcome {
            override fun toString(): String = "Refreshed(<redacted>)"
        }

        data object NoSession : RefreshOutcome

        /** The store threw [TokenStoreUnavailableException]: the session may be intact, it just cannot be read now. */
        data object StoreUnavailable : RefreshOutcome

        data class Ended(val reason: SessionEndReason) : RefreshOutcome

        class Unavailable(val error: DataError.Network, val stored: StoredSession) : RefreshOutcome
    }
}
