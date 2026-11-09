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
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.domain.report.FiledReport
import com.moyi.app.domain.report.ReportReason
import com.moyi.app.domain.report.ReportTarget
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * [ReportRepositoryImpl] against a MockEngine through the real authed client: each target's
 * path, the `Idempotency-Key` on both (the member route's added by hand, contract gap row
 * 32), the reason by its exact name, a blank detail left out, the `201` mapped, and each
 * refusal reaching the caller by its code.
 */
class ReportRepositoryTest {
    private class Fixture(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        private val config = ApiConfig("http://localhost:8080/api/v1")
        private val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        private val sessionManager = SessionManager(InMemoryTokenStore(), RefreshControllerApi(config.origin, publicClient.client), NoPersonalData)
        val repo = ReportRepositoryImpl(AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager)), config)

        fun json(): JsonObject = Json.parseToJsonElement(assertIs<TextContent>(requests.single().body).text).jsonObject
    }

    @Test
    fun anEntryReportIsPostedToTheEntryWithItsKeyAndMapped() = runTest {
        val fixture = Fixture {
            respondJson("""{"id":"r1","entryId":"e1","reason":"HARASSMENT"}""", HttpStatusCode.Created)
        }

        val result = fixture.repo.report(ReportTarget.Entry("e1"), ReportReason.HARASSMENT, "they keep saying it", "k1")

        assertEquals(Result.Success(FiledReport(id = "r1", reason = ReportReason.HARASSMENT)), result)
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/entries/e1/report", request.url.encodedPath)
        assertEquals("k1", request.headers["Idempotency-Key"])
        assertEquals(JsonPrimitive("HARASSMENT"), fixture.json()["reason"])
        assertEquals(JsonPrimitive("they keep saying it"), fixture.json()["detail"])
    }

    @Test
    fun aMemberReportIsPostedToTheOtherMemberWithTheKeyTheContractLeavesOut() = runTest {
        val fixture = Fixture { respondJson("""{"id":"r2","reason":"OTHER"}""", HttpStatusCode.Created) }

        val result = fixture.repo.report(ReportTarget.Member("b1"), ReportReason.OTHER, null, "k2")

        assertEquals(Result.Success(FiledReport(id = "r2", reason = ReportReason.OTHER)), result)
        val request = fixture.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/bonds/b1/members/other/report", request.url.encodedPath)
        assertEquals("k2", request.headers["Idempotency-Key"])
        assertEquals(JsonPrimitive("OTHER"), fixture.json()["reason"])
    }

    @Test
    fun aBlankDetailIsLeftOutRatherThanRefused() = runTest {
        val fixture = Fixture { respondJson("""{"id":"r3","reason":"OTHER"}""", HttpStatusCode.Created) }

        fixture.repo.report(ReportTarget.Member("b1"), ReportReason.OTHER, "   ", "k3")

        val detail = fixture.json()["detail"]
        assertFalse(detail is JsonPrimitive && detail.isString, "a blank detail must not be sent: $detail")
    }

    @Test
    fun everyReasonIsSentByItsExactName() = runTest {
        ReportReason.entries.forEach { reason ->
            val fixture = Fixture { respondJson("""{"id":"r","reason":"${reason.name}"}""", HttpStatusCode.Created) }
            val result = fixture.repo.report(ReportTarget.Member("b1"), reason, null, "k")
            assertEquals(JsonPrimitive(reason.name), fixture.json()["reason"])
            assertEquals(reason, assertIs<Result.Success<FiledReport>>(result).data.reason)
        }
    }

    @Test
    fun aReportOnMyOwnEntryReachesTheCallerByItsCode() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Conflict, "REPORT_ON_OWN_ENTRY") }

        val result = fixture.repo.report(ReportTarget.Entry("mine"), ReportReason.OTHER, null, "k")

        val error = assertIs<DataError.Network.Api>(assertIs<Result.Failure<DataError.Network>>(result).error)
        assertEquals(409, error.problem.status)
        assertEquals(ApiErrorCode.REPORT_ON_OWN_ENTRY, error.problem.code)
    }

    @Test
    fun theDailyAllowanceReachesTheCallerByItsCode() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.TooManyRequests, "RATE_LIMITED") }

        val result = fixture.repo.report(ReportTarget.Member("b1"), ReportReason.OTHER, null, "k")

        val error = assertIs<DataError.Network.Api>(assertIs<Result.Failure<DataError.Network>>(result).error)
        assertEquals(ApiErrorCode.RATE_LIMITED, error.problem.code)
    }

    @Test
    fun aCreatedBodyMissingItsIdIsASerializationError() = runTest {
        val fixture = Fixture { respondJson("""{"reason":"OTHER"}""", HttpStatusCode.Created) }

        val result = fixture.repo.report(ReportTarget.Member("b1"), ReportReason.OTHER, null, "k")

        assertEquals(Result.Failure(DataError.Network.Serialization), result)
    }
}
