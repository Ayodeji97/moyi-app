package com.moyi.app.data.network

import com.moyi.app.api.apis.LoginControllerApi
import com.moyi.app.api.models.LoginRequest
import com.moyi.app.api.models.LoginResponse
import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Drives the GENERATED LoginControllerApi through our client, over MockEngine. */
class LoginControllerApiTest {
    private val config = ApiConfig("http://localhost:8080/api/v1")
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(status: HttpStatusCode, contentType: String, body: String): LoginControllerApi {
        val engine = MockEngine { request ->
            requests += request
            respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
        }
        return LoginControllerApi(config.origin, HttpClientFactory.createPublicClient(engine))
    }

    private val request = LoginRequest(email = "ada@example.test", password = "correct horse")

    @Test
    fun a200MapsToALoginResponse() = runTest {
        val api = api(
            HttpStatusCode.OK,
            "application/json",
            """{"accessToken":"a.b.c","expiresIn":900,"refreshToken":"r1","user":{"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7","email":"ada@example.test","displayName":"Ada","emailVerified":true,"status":"ACTIVE"},"addedLater":1}""",
        )

        val result = safeApiCall { api.login(request) }

        val response = assertIs<Result.Success<LoginResponse>>(result).data
        assertEquals("a.b.c", response.accessToken)
        assertEquals(900L, response.expiresIn)
        assertEquals("r1", response.refreshToken)
        assertEquals("Ada", response.user?.displayName)
    }

    @Test
    fun a401ProblemIsInvalidCredentialsNotAnEmptyLoginResponse() = runTest {
        val api = api(
            HttpStatusCode.Unauthorized,
            "application/problem+json",
            """{"type":"about:blank","title":"Unauthorized","status":401,"code":"INVALID_CREDENTIALS","detail":"Wrong email or password."}""",
        )

        when (val result = safeApiCall { api.login(request) }) {
            is Result.Success -> fail("A problem body decoded as LoginResponse: ${result.data}")
            is Result.Failure -> assertEquals(
                DataError.Network.Api(ApiProblem(401, ApiErrorCode.INVALID_CREDENTIALS)),
                result.error,
            )
        }
    }

    @Test
    fun theRequestPathIsApiV1AuthLoginExactlyOnce() = runTest {
        val api = api(HttpStatusCode.OK, "application/json", """{"accessToken":"a"}""")

        safeApiCall { api.login(request) }

        val sent = requests.single()
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("http://localhost:8080/api/v1/auth/login", sent.url.toString())
        assertEquals("/api/v1/auth/login", sent.url.encodedPath)
    }

    @Test
    fun theBodyIsJsonAndANullDeviceIsLeftOut() = runTest {
        val api = api(HttpStatusCode.OK, "application/json", """{"accessToken":"a"}""")

        safeApiCall { api.login(request) }

        val body = assertIs<TextContent>(requests.single().body)
        assertEquals("""{"email":"ada@example.test","password":"correct horse"}""", body.text)
        assertTrue(requests.single().headers[HttpHeaders.Accept].orEmpty().contains("application/json"))
    }

    @Test
    fun a502HtmlPageIsAServerError() = runTest {
        val api = api(HttpStatusCode.BadGateway, "text/html", "<html>Bad Gateway</html>")

        assertEquals(Result.Failure(DataError.Network.Server(502)), safeApiCall { api.login(request) })
    }

    @Test
    fun a200WithABodyThatIsNotTheContractIsSerialization() = runTest {
        val api = api(HttpStatusCode.OK, "application/json", """{"accessToken":""")

        assertEquals(Result.Failure(DataError.Network.Serialization), safeApiCall { api.login(request) })
    }
}

