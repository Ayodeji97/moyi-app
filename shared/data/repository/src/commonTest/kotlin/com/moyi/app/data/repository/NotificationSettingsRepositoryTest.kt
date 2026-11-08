package com.moyi.app.data.repository

import com.moyi.app.api.apis.RefreshControllerApi
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
import com.moyi.app.domain.notification.NotificationCategory
import com.moyi.app.domain.notification.NotificationPreferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** [NotificationSettingsRepositoryImpl]: the toggles read, and a change that names only its own toggle. */
class NotificationSettingsRepositoryTest {
    private val config = ApiConfig("http://localhost:8080/api/v1")
    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(body: String): NotificationSettingsRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request
            respondJson(body)
        }
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(InMemoryTokenStore(), RefreshControllerApi(config.origin, publicClient.client), NoPersonalData)
        return NotificationSettingsRepositoryImpl(AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager)), config)
    }

    @Test
    fun readsEveryToggle() = runTest {
        val result = repo(ALL_BUT_ON_THIS_DAY).preferences("b1")

        assertEquals(
            Result.Success(NotificationPreferences(NotificationCategory.entries.toSet() - NotificationCategory.OnThisDay)),
            result,
        )
        assertEquals("/api/v1/bonds/b1/members/me/notifications", requests.single().url.encodedPath)
    }

    @Test
    fun aChangeSendsOnlyItsOwnToggle() = runTest {
        repo(ALL_BUT_ON_THIS_DAY).change("b1", NotificationCategory.Milestone, on = false)

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        val categories = Json.parseToJsonElement(assertIs<TextContent>(request.body).text).jsonObject["categories"] as JsonObject
        // Null or absent both mean "keep" to the server; only milestone may carry a value.
        assertEquals(JsonPrimitive(false), categories["milestone"])
        assertEquals(emptyList(), categories.filterKeys { it != "milestone" }.values.filter { it != kotlinx.serialization.json.JsonNull })
    }

    @Test
    fun aMissingToggleIsABodyThatDidNotParse() = runTest {
        val result = repo("""{"categories":{"reminder":true}}""").preferences("b1")

        assertEquals(Result.Failure(DataError.Network.Serialization), result)
    }

    private companion object {
        const val ALL_BUT_ON_THIS_DAY =
            """{"categories":{"reminder":true,"partnerJoined":true,"reveal":true,"milestone":true,"onThisDay":false,"proposal":true}}"""
    }
}
