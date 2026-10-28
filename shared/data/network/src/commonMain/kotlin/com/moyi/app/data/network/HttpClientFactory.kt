package com.moyi.app.data.network

import co.touchlab.kermit.Logger as KermitLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

/**
 * Builds the app's HttpClients. The generated `*ControllerApi(origin, client)`
 * classes take one of these; they never build their own.
 *
 * Both clients are built over ONE [HttpClientEngine] ([platformEngine], a Koin
 * single), so they share a connection pool and threads. A client never closes
 * an engine it was handed, so the engine lives as long as the app.
 *
 * Two clients, by design (plan, Auth design):
 * - [createPublicClient], with no Auth plugin, for register, verify, resend,
 *   login, refresh and logout. A wrong password's 401 must not trigger a
 *   token refresh.
 * - [createAuthedClient]: the same, plus Ktor's bearer Auth plugin, which asks
 *   a [BearerTokenSource] for the token and for a refresh on a 401.
 */
object HttpClientFactory {
    /** The public client over [engine]: the app's shared [platformEngine], or MockEngine in tests. */
    fun createPublicClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) { configureCommon() }

    /** The authed client over [engine]; share the public client's engine so both use one connection pool. */
    fun createAuthedClient(engine: HttpClientEngine, tokens: BearerTokenSource): HttpClient = HttpClient(engine) {
        configureCommon()
        configureBearerAuth(tokens)
    }

    /**
     * Ktor's bearer provider over [tokens].
     *
     * - `cacheTokens = false`: Ktor asks [tokens] on every request instead of
     *   caching what it loaded. The session holds the one copy of the access
     *   token, so a sign-out, a new sign-in or a revoked session can never
     *   leave a stale token in a Ktor cache (and no `clearToken()` call has to
     *   be remembered at each of those places).
     * - The stale token handed to [BearerTokenSource.refreshAccessToken] is
     *   read from THE FAILED REQUEST's Authorization header, not `oldTokens`:
     *   only that header says which token the server refused, so a late 401
     *   for an already-replaced token cannot start a second rotation (which
     *   the server would read as reuse and end every session, ADR-0021).
     * - `nonCancellableRefresh`: once a refresh is on the wire, closing the
     *   screen that caused it must not abandon the answer. The server has
     *   rotated; dropping the new token would make the next refresh a reuse.
     * - The refresh token never passes through Ktor (`BearerTokens(access, null)`):
     *   only the session reads it, from secure storage.
     */
    private fun HttpClientConfig<*>.configureBearerAuth(tokens: BearerTokenSource) {
        install(Auth) {
            bearer {
                cacheTokens = false
                nonCancellableRefresh = true
                loadTokens { tokens.currentAccessToken()?.let { BearerTokens(it, null) } }
                refreshTokens {
                    val stale = response.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
                    tokens.refreshAccessToken(stale)?.let { BearerTokens(it, null) }
                }
                sendWithoutRequest { true }
            }
        }
    }

    /**
     * What every Moyi client shares.
     *
     * The logger runs at INFO: method, URL and status only. It never logs
     * bodies (passwords, tokens, entry text), the Authorization header is
     * sanitised in case the level is ever raised to HEADERS, and query strings
     * are cut from every line, because search sends its terms as `?q=`
     * (CLAUDE.md, Code).
     */
    internal fun HttpClientConfig<*>.configureCommon() {
        expectSuccess = false // the generated HttpResponse reports success; safeApiCall maps failures

        install(ContentNegotiation) { json(MoyiJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                private val log = KermitLogger.withTag("Http")
                override fun log(message: String) = log.i { redactQueries(message) }
            }
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
        defaultRequest {
            // problem+json too: Spring answers errors with it (doc 06 §2).
            // Accept-Language from the device comes with the auth screens.
            headers.append(HttpHeaders.Accept, "${ContentType.Application.Json}, ${ContentType.Application.ProblemJson}")
        }
    }
}

private val queryInUrl = Regex("""(https?://[^\s?#]+)\?[^\s#]*""")

/** Replaces every URL query string in [message] with `?…`, so search terms never reach a log. */
internal fun redactQueries(message: String): String = queryInUrl.replace(message) { "${it.groupValues[1]}?…" }

/** This platform's engine: OkHttp on Android, Darwin (NSURLSession) on iOS. Create one per app. */
expect fun platformEngine(): HttpClientEngine
