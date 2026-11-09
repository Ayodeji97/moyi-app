package com.moyi.app.data.repository

import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.HttpClientFactory
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.data.testing.NoPersonalData
import com.moyi.app.data.testing.respondJson
import com.moyi.app.domain.support.SupportTicket
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/** [SupportRepositoryImpl]: what is sent, the ticket read back, and each refusal kept as the server said it. */
class SupportRepositoryTest {
    private val config = ApiConfig("http://localhost:8080/api/v1")
    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(answer: MockRequestHandleScope.() -> HttpResponseData): SupportRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request
            answer()
        }
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(InMemoryTokenStore(), RefreshControllerApi(config.origin, publicClient.client), NoPersonalData)
        return SupportRepositoryImpl(AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager)), config)
    }

    @Test
    fun sendsSubjectBodyAndTheKeyAndReadsTheTicket() = runTest {
        val result = repo { respondJson(TICKET, HttpStatusCode.Created) }.send("Sign in", "It will not let me in.", "key-1")

        assertEquals(Result.Success(SupportTicket("7d8c3b52-5f0c-4c4e-9a52-0b1f3c6d2e11", Instant.parse("2026-11-09T10:15:30Z"))), result)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/support/messages", request.url.encodedPath)
        assertEquals("key-1", request.headers["Idempotency-Key"])
        val body = Json.parseToJsonElement(assertIs<TextContent>(request.body).text).jsonObject
        assertEquals(JsonPrimitive("Sign in"), body["subject"])
        assertEquals(JsonPrimitive("It will not let me in."), body["body"])
    }

    @Test
    fun aReplayIsTheSameTicket() = runTest {
        val result = repo {
            respond(TICKET, HttpStatusCode.Created, headersOf("Content-Type" to listOf("application/json"), "Idempotency-Replayed" to listOf("true")))
        }.send("Sign in", "Again.", "key-1")

        assertEquals("7d8c3b52-5f0c-4c4e-9a52-0b1f3c6d2e11", assertIs<Result.Success<SupportTicket>>(result).data.id)
    }

    @Test
    fun theSixthOfTheDayIsRateLimitedWithItsTraceId() = runTest {
        val result = repo { respondProblemWithTrace(HttpStatusCode.TooManyRequests, "RATE_LIMITED", errors = "[]") }.send("s", "b", "k")

        val problem = assertIs<DataError.Network.Api>(assertIs<Result.Failure<DataError.Network>>(result).error).problem
        assertEquals(429, problem.status)
        assertEquals(ApiErrorCode.RATE_LIMITED, problem.code)
        assertEquals("0af7651916cd43dd8448eb211c80319c", problem.traceId)
    }

    @Test
    fun aRefusedFieldIsNamed() = runTest {
        val result = repo {
            respondProblemWithTrace(
                HttpStatusCode.UnprocessableEntity,
                "VALIDATION_FAILED",
                errors = """[{"field":"body","code":"VALID_SUPPORT_BODY","message":"is too long"}]""",
            )
        }.send("s", "b", "k")

        val problem = assertIs<DataError.Network.Api>(assertIs<Result.Failure<DataError.Network>>(result).error).problem
        assertEquals(ApiErrorCode.VALIDATION_FAILED, problem.code)
        assertEquals("body", problem.fieldErrors.single().field)
    }

    @Test
    fun aTicketWithoutItsIdDidNotParse() = runTest {
        val result = repo { respondJson("""{"createdAt":"2026-11-09T10:15:30Z"}""", HttpStatusCode.Created) }.send("s", "b", "k")

        assertEquals(Result.Failure(DataError.Network.Serialization), result)
    }

    private fun MockRequestHandleScope.respondProblemWithTrace(status: HttpStatusCode, code: String, errors: String): HttpResponseData =
        respond(
            """{"type":"about:blank","title":"${status.description}","status":${status.value},"code":"$code","errors":$errors,"traceId":"0af7651916cd43dd8448eb211c80319c"}""",
            status,
            headersOf("Content-Type", "application/problem+json"),
        )

    private companion object {
        const val TICKET = """{"id":"7d8c3b52-5f0c-4c4e-9a52-0b1f3c6d2e11","createdAt":"2026-11-09T10:15:30Z"}"""
    }
}
