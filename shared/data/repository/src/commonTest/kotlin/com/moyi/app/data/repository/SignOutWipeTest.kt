package com.moyi.app.data.repository

import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.HttpClientFactory
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.session.PersonalData
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.data.testing.inMemoryDatabase
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.data.testing.storedSession
import com.moyi.app.domain.auth.SessionEndReason
import com.moyi.app.domain.auth.SessionState
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.CachedDay
import com.moyi.app.domain.today.Draft
import com.moyi.app.domain.today.DraftKind
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Today
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The privacy rule of the local database, end to end through the real session, the
 * real auth repository and a real (in-memory) SQLite: when a session ends, for any
 * reason, nothing of that person's day or unsent words is left, and the next person to sign in on
 * the same phone finds no trace of it.
 */
class SignOutWipeTest {
    private val config = ApiConfig("http://localhost:8080/api/v1")

    private inner class Fixture(refreshAnswer: String = "") {
        val database = inMemoryDatabase()
        val store = InMemoryTokenStore()
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/login" -> respondJson(loginAnswer(request))
                "/api/v1/auth/logout" -> respondJson("", HttpStatusCode.NoContent)
                else -> respondProblem(HttpStatusCode.Unauthorized, refreshAnswer)
            }
        }
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(store, RefreshControllerApi(config.origin, publicClient.client), PersonalData { database.wipe() })
        val auth = AuthRepositoryImpl(publicClient, config, sessionManager) { DeviceInfo("ANDROID", "1.0", "16") }
        val cache = SqlTodayCache(database, sessionManager.sessionState)
        val drafts = SqlDraftStore(database, sessionManager.sessionState)

        /** Ada's day and both of her drafts, as a session would leave them. */
        suspend fun writeAdasData() {
            cache.write(adasDay)
            drafts.write(Draft("bond-1", DraftKind.NewEntry, "You fixed the gate.", "2026-09-14"))
            drafts.write(Draft("bond-1", DraftKind.Edit, "You made the tea, twice.", "2026-09-14", entryId = "e1"))
        }

        fun assertNothingLeft(message: String? = null) {
            assertEquals(0L, database.rowCount("cached_day"), message)
            assertEquals(0L, database.rowCount("draft"), message)
        }
    }

    /** Each email signs in as its own person. */
    private fun loginAnswer(request: HttpRequestData): String {
        val ben = "ben@" in assertIs<TextContent>(request.body).text
        val (id, name) = if (ben) "ben-id" to "Ben" else "ada-id" to "Ada"
        val user = """{"id":"$id","email":"${name.lowercase()}@example.test","displayName":"$name","emailVerified":true,"status":"ACTIVE"}"""
        return """{"accessToken":"a-$id","expiresIn":900,"refreshToken":"r-$id","user":$user}"""
    }

    private val adasDay = CachedDay(
        bond = Bond("bond-1", "Ada and Tunde", BondType.COUPLE, BondStatus.ACTIVE, invite = null, partnerName = "Tunde", anchorTimezone = "Europe/London"),
        today = Today(
            date = "2026-09-14",
            status = BondDayStatus.REVEALED,
            myEntry = MyEntry("e1", "You made the tea.", EntryStatus.REVEALED),
            partnerEntry = PartnerEntry.Revealed("e2", "You listened."),
            prompt = null,
        ),
    )

    @Test
    fun signingOutWipesTheDayAndTheNextPersonOnThePhoneFindsNothing() = runTest {
        val fixture = Fixture()
        fixture.auth.login("ada@example.test", "pw")
        fixture.writeAdasData()
        assertNotNull(fixture.cache.read())
        assertNotNull(fixture.drafts.read("bond-1", DraftKind.NewEntry))

        fixture.auth.logout()

        fixture.assertNothingLeft("sign-out leaves no row behind, no day and no draft")
        assertNull(fixture.cache.read())

        fixture.auth.login("ben@example.test", "pw")
        assertEquals("ben-id", (fixture.sessionManager.sessionState.value as SessionState.SignedIn).user.id)
        assertNull(fixture.cache.read(), "Ben must see nothing of Ada's day")
        assertNull(fixture.drafts.read("bond-1", DraftKind.NewEntry), "nor her unsent words")
        fixture.assertNothingLeft()
    }

    @Test
    fun aDifferentAccountSigningInWipesEvenIfTheLastSessionNeverEndedHere() = runTest {
        // Ada's session is replaced by Ben's without a sign-out in between (the shell
        // never allows it today, but the wipe must not depend on that).
        val fixture = Fixture()
        fixture.auth.login("ada@example.test", "pw")
        fixture.writeAdasData()

        fixture.auth.login("ben@example.test", "pw")

        fixture.assertNothingLeft()
    }

    @Test
    fun aSessionTheServerEndsWipesTheDayToo() = runTest {
        for (code in listOf("REFRESH_TOKEN_INVALID", "TOKEN_REUSE_DETECTED")) {
            val fixture = Fixture(refreshAnswer = code)
            fixture.auth.login("ada@example.test", "pw")
            fixture.writeAdasData()

            // The access token is refused and the refresh is answered with [code]: the session is over.
            assertNull(fixture.sessionManager.refresh(staleAccess = "a-ada-id"))

            assertEquals(SessionState.SignedOut, fixture.sessionManager.sessionState.value, code)
            fixture.assertNothingLeft(code)
        }
    }

    @Test
    fun endingASessionLocallyWipesTheDay() = runTest {
        val fixture = Fixture()
        fixture.store.write(storedSession("r1"))
        fixture.auth.login("ada@example.test", "pw")
        fixture.writeAdasData()

        fixture.sessionManager.endSession(SessionEndReason.Invalid)

        fixture.assertNothingLeft()
    }
}
