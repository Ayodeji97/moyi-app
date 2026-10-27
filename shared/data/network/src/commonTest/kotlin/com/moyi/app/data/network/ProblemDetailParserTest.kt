package com.moyi.app.data.network

import com.moyi.app.api.models.ProblemDetail
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.FieldError
import kotlin.test.Test
import kotlin.test.assertEquals

class ProblemDetailParserTest {
    private val problemJson = "application/problem+json"

    @Test
    fun decodesAnUndeclared401InvalidCredentials() {
        // POST /auth/login does not declare 401 (contract gap 1); the body still decodes.
        val body = """{"type":"about:blank","title":"Unauthorized","status":401,"code":"INVALID_CREDENTIALS","detail":"Wrong email or password.","instance":"/api/v1/auth/login"}"""
        assertEquals(
            ApiProblem(status = 401, code = ApiErrorCode.INVALID_CREDENTIALS),
            ProblemDetailParser.parse(401, body, problemJson),
        )
    }

    @Test
    fun decodesAnUndeclared410() {
        val body = """{"type":"about:blank","title":"Gone","status":410,"code":"VERIFICATION_TOKEN_EXPIRED"}"""
        assertEquals(ApiErrorCode.VERIFICATION_TOKEN_EXPIRED, ProblemDetailParser.parse(410, body, problemJson).code)
    }

    @Test
    fun mapsAnUnknownCodeToUnknownAndKeepsTheRest() {
        val body = """{"type":"about:blank","title":"x","status":409,"code":"SOMETHING_NEW","errors":[{"field":"a","code":"B","message":"c"}]}"""
        val problem = ProblemDetailParser.parse(409, body, problemJson)
        assertEquals(ApiErrorCode.Unknown, problem.code)
        assertEquals(409, problem.status)
        assertEquals(listOf(FieldError("a", "B", "c")), problem.fieldErrors)
    }

    @Test
    fun aCodeNamedUnknownIsNotMistakenForOurFallback() {
        val body = """{"status":400,"code":"Unknown"}"""
        assertEquals(ApiErrorCode.Unknown, ProblemDetailParser.parse(400, body, problemJson).code)
    }

    @Test
    fun mapsFieldErrorsAndSkipsMalformedOnes() {
        val body = """
            {"type":"about:blank","title":"Bad Request","status":400,"code":"VALIDATION_FAILED",
             "traceId":"0af7651916cd43dd8448eb211c80319c",
             "errors":[
               {"field":"password","code":"NOT_BREACHED","message":"Choose another password."},
               {"field":"email","code":"EMAIL"},
               {"code":"NO_FIELD","message":"skipped"},
               "not an object"
             ]}
        """.trimIndent()
        assertEquals(
            ApiProblem(
                status = 400,
                code = ApiErrorCode.VALIDATION_FAILED,
                fieldErrors = listOf(
                    FieldError("password", "NOT_BREACHED", "Choose another password."),
                    FieldError("email", "EMAIL", ""),
                ),
                traceId = "0af7651916cd43dd8448eb211c80319c",
            ),
            ProblemDetailParser.parse(400, body, "application/problem+json;charset=UTF-8"),
        )
    }

    @Test
    fun aNonJsonBodyIsUnknownWithTheHttpStatus() {
        val html = "<html><body>502 Bad Gateway</body></html>"
        assertEquals(ApiProblem(502, ApiErrorCode.Unknown), ProblemDetailParser.parse(502, html, "text/html"))
        assertEquals(ApiProblem(502, ApiErrorCode.Unknown), ProblemDetailParser.parse(502, html, null))
        assertEquals(ApiProblem(500, ApiErrorCode.Unknown), ProblemDetailParser.parse(500, "", problemJson))
        assertEquals(ApiProblem(400, ApiErrorCode.Unknown), ProblemDetailParser.parse(400, "[1,2]", problemJson))
        assertEquals(ApiProblem(400, ApiErrorCode.Unknown), ProblemDetailParser.parse(400, "{\"code\":", problemJson))
    }

    @Test
    fun theHttpStatusWinsOverTheBodyStatus() {
        val body = """{"status":200,"code":"RATE_LIMITED"}"""
        assertEquals(429, ProblemDetailParser.parse(429, body, problemJson).status)
    }

    @Test
    fun everyGeneratedCodeHasAnAppCodeOfTheSameName() {
        // ApiErrorCode (core) and ProblemDetail.Code (OpenAPI generator) both come
        // from contracts/openapi.json; this proves the two generators agree.
        assertEquals(
            ProblemDetail.Code.entries.map { it.value },
            ApiErrorCode.entries.filter { it != ApiErrorCode.Unknown }.map { it.name },
        )
    }
}
