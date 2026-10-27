@file:OptIn(ExperimentalAtomicApi::class)

package com.moyi.app.data.session

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import com.moyi.app.api.apis.MeControllerApi
import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.data.network.HttpClientFactory
import com.moyi.app.data.testing.EventLog
import com.moyi.app.data.testing.InMemoryTokenStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

const val ORIGIN = "http://localhost:8080"

/**
 * One MockEngine behind both clients, a fake store and an ordered log.
 * [handler] answers every request; [refreshCalls] counts `POST /auth/refresh`.
 * [dispatcher] (a test dispatcher) keeps the engine on the test's virtual
 * thread when a test needs to run everything up to a gate with `runCurrent()`.
 */
class TestServer(
    initial: StoredSession?,
    dispatcher: CoroutineDispatcher? = null,
    val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val log = EventLog()

    /** Runs inside every store write, before the session is kept; lets a test see what was published at that moment. */
    var onStoreWrite: (StoredSession) -> Unit = {}
    val store = InMemoryTokenStore(initial, log) { onStoreWrite(it) }
    val refreshCalls = AtomicInt(0)

    private val engine = MockEngine(
        MockEngineConfig().apply {
            dispatcher?.let { this.dispatcher = it }
            addHandler { request ->
                val auth = request.headers[HttpHeaders.Authorization] ?: "no-auth"
                log.add("${request.method.value} ${request.url.encodedPath} $auth")
                if (request.url.encodedPath == "/api/v1/auth/refresh") refreshCalls.incrementAndFetch()
                handler(request)
            }
        },
    )

    val publicClient = HttpClientFactory.createPublicClient(engine)
    val logs = CapturingLogWriter()
    val sessionManager = SessionManager(
        store,
        RefreshControllerApi(ORIGIN, publicClient),
        Logger(StaticConfig(logWriterList = listOf(logs))),
    )
    val authedClient = HttpClientFactory.createAuthedClient(engine, sessionManager)
    val me = MeControllerApi(ORIGIN, authedClient)
}

val HttpRequestData.bearer: String? get() = headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
val HttpRequestData.isRefresh: Boolean get() = url.encodedPath == "/api/v1/auth/refresh"
val HttpRequestData.isMe: Boolean get() = url.encodedPath == "/api/v1/me"
