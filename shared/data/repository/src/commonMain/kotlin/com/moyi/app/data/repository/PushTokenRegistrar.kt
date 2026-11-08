package com.moyi.app.data.repository

import co.touchlab.kermit.Logger
import com.moyi.app.api.apis.PushTokenControllerApi
import com.moyi.app.api.models.PushTokenRequest
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCallEmpty
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.notification.PushTokenRegistration
import com.moyi.app.domain.notification.PushTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Tells the server this device's push token (`PUT /me/devices/current/push-token`,
 * ADR-0044) whenever someone is signed in here and [source] has a token: once per
 * sign-in or launch, and again when the provider rotates the token. A `PUT` names a
 * state, so sending the same one twice changes nothing; one that failed is tried again on
 * the next foreground ([retry]).
 *
 * **On a voluntary sign-out, [forget] runs first**, before the session's tokens are
 * cleared: the `DELETE` needs the bearer. It is bounded, so an offline sign-out waits
 * at most [FORGET_LIMIT] for it. The server also forgets the token in the same commit
 * as the logout itself (ADR-0044 decision 2), so this is the belt to that braces: it
 * matters when the logout call is the one that does not arrive.
 *
 * **An involuntary end** (a reuse, a refresh the server refused, a session ended from
 * another phone) cannot call the API: there is no session left to call it with. The
 * server forgets the token with the session in every one of those cases (ADR-0044), so
 * nothing is owed here except forgetting what was registered.
 *
 * The token is a secret device identifier: it is never logged, only the outcome.
 */
class PushTokenRegistrar(
    private val source: PushTokenSource,
    authedClient: AuthedHttpClient,
    config: ApiConfig,
    private val session: StateFlow<SessionState>,
    private val scope: CoroutineScope,
    logger: Logger = Logger,
) : PushTokenRegistration {
    private val api = PushTokenControllerApi(config.origin, authedClient.client)
    private val log = logger.withTag("PushToken")
    private val mutex = Mutex()

    /** What the server was last told, for whom; guarded by [mutex]. */
    private var registered: Registration? = null
    private val retries = MutableStateFlow(0)
    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(signedInUser(), source.token, retries) { user, token, _ -> user to token }
                .collect { (user, token) -> sync(user, token) }
        }
    }

    /** The app came to the foreground: a registration that failed is tried again. */
    override fun retry() {
        retries.value++
    }

    /**
     * Voluntary sign-out only, while the session still holds its tokens. Sends the
     * `DELETE` when this device may have registered a token (it has one now, or one was
     * registered in this process), and gives up after [FORGET_LIMIT]. Never throws: a
     * sign-out goes on whatever the answer.
     */
    suspend fun forget() {
        withTimeoutOrNull(FORGET_LIMIT) {
            mutex.withLock {
                val token = source.token.first()
                if (token == null && registered == null) return@withLock
                when (val result = safeApiCallEmpty { api.forgetPushToken() }) {
                    is Result.Success -> log.i { "Push token forgotten before sign-out" }
                    is Result.Failure -> log.w { "Push token not forgotten before sign-out: ${result.error::class.simpleName}" }
                }
                registered = null
            }
        } ?: log.w { "Push token not forgotten before sign-out: timed out" }
    }

    private fun signedInUser(): Flow<String?> =
        session.map { (it as? SessionState.SignedIn)?.user?.id }.distinctUntilChanged()

    private suspend fun sync(userId: String?, token: String?) = mutex.withLock {
        if (userId == null) {
            // The session ended, and the server forgot the token with it (ADR-0044).
            registered = null
            return@withLock
        }
        if (token == null) return@withLock
        val wanted = Registration(userId, token)
        if (registered == wanted) return@withLock
        when (val result = safeApiCallEmpty { api.registerPushToken(PushTokenRequest(token)) }) {
            is Result.Success -> {
                registered = wanted
                log.i { "Push token registered" }
            }
            is Result.Failure -> log.w { "Push token not registered: ${result.error::class.simpleName}" }
        }
    }

    private class Registration(val userId: String, val token: String) {
        override fun equals(other: Any?): Boolean = other is Registration && other.userId == userId && other.token == token

        override fun hashCode(): Int = 31 * userId.hashCode() + token.hashCode()

        override fun toString(): String = "Registration(<redacted>)"
    }

    companion object {
        /** How long a sign-out waits for the `DELETE` before it goes on without it. */
        val FORGET_LIMIT = 5.seconds
    }
}

/**
 * Until Firebase is set up there is no push provider, so there is never a token and
 * nothing is registered. **The one place to swap in FCM**: bind an FCM-backed
 * [PushTokenSource] in [repositoryModule] instead of this.
 */
object NoPushTokenSource : PushTokenSource {
    override val token: Flow<String?> = flowOf(null)
}
