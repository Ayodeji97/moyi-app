package com.moyi.app.data.network

import co.touchlab.kermit.Logger as KermitLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

/**
 * Builds the app's HttpClients. The generated `*ControllerApi(origin, client)`
 * classes take one of these; they never build their own.
 *
 * Two clients, by design (plan, Auth design):
 * - [createPublicClient], with no Auth plugin, for register, verify, resend,
 *   login, refresh and logout. A wrong password's 401 must not trigger a
 *   token refresh.
 * - The authed client, with Ktor's bearer Auth plugin and the single-flight
 *   refresh, arrives in PR #4. It is [configureCommon] plus `install(Auth)`.
 */
object HttpClientFactory {
    fun createPublicClient(): HttpClient = platformHttpClient { configureCommon() }

    /** For tests: the same configuration over a given engine, such as MockEngine. */
    fun createPublicClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) { configureCommon() }

    /**
     * What every Moyi client shares. The seam for PR #4's authed client.
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

/** An HttpClient on this platform's engine: OkHttp on Android, Darwin (NSURLSession) on iOS. */
internal expect fun platformHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient
