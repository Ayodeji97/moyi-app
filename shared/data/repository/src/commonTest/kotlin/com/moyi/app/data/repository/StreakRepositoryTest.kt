package com.moyi.app.data.repository

import com.moyi.app.api.apis.RefreshControllerApi
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.HttpClientFactory
import com.moyi.app.data.network.PublicHttpClient
import com.moyi.app.data.session.SessionManager
import com.moyi.app.data.testing.EventLog
import com.moyi.app.data.testing.InMemoryTokenStore
import com.moyi.app.data.testing.NoPersonalData
import com.moyi.app.data.testing.respondJson
import com.moyi.app.data.testing.respondProblem
import com.moyi.app.domain.streak.FrozenReason
import com.moyi.app.domain.streak.Streak
import com.moyi.app.domain.streak.StreakDay
import com.moyi.app.domain.streak.StreakDayStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [StreakRepositoryImpl] against a MockEngine, through the real authed client: the path the
 * contract declares, every one of the five cell values, the run the last break ended, each
 * frozen reason and the neutral fallback for one this build does not know, and a body missing what the screen
 * draws failing as Serialization rather than being drawn as zero.
 */
class StreakRepositoryTest {
    private class Fixture(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val publicClient = PublicHttpClient(HttpClientFactory.createPublicClient(engine))
        val sessionManager = SessionManager(
            InMemoryTokenStore(log = EventLog()),
            RefreshControllerApi(ApiConfig("http://localhost:8080/api/v1").origin, publicClient.client),
            NoPersonalData,
        )
        val authed = AuthedHttpClient(HttpClientFactory.createAuthedClient(engine, sessionManager))
        val streak = StreakRepositoryImpl(authed, ApiConfig("http://localhost:8080/api/v1"))

        val request: HttpRequestData get() = requests.single()
    }

    @Test
    fun theStreakIsReadWithEveryCellValue() = runTest {
        val fixture = Fixture {
            respondJson(
                """
                {"current":3,"longest":41,"freezesAvailable":1,"freezeProgress":4,"strictMode":false,
                 "totalCompleteDays":57,"lastCompleteDate":"2026-09-13",
                 "days":[
                   {"date":"2026-09-10","status":"MISSED"},
                   {"date":"2026-09-11","status":"SOLO"},
                   {"date":"2026-09-12","status":"FROZEN"},
                   {"date":"2026-09-13","status":"COMPLETE"},
                   {"date":"2026-09-14","status":"OPEN"}
                 ]}
                """.trimIndent(),
            )
        }

        val result = fixture.streak.streak("b1")

        assertEquals(
            Result.Success(
                Streak(
                    current = 3,
                    longest = 41,
                    freezesAvailable = 1,
                    freezeProgress = 4,
                    strictMode = false,
                    totalCompleteDays = 57,
                    lastCompleteDate = "2026-09-13",
                    days = listOf(
                        StreakDay("2026-09-10", StreakDayStatus.MISSED),
                        StreakDay("2026-09-11", StreakDayStatus.SOLO),
                        // No reason sent on a FROZEN cell: the neutral fallback, never a guess.
                        StreakDay("2026-09-12", StreakDayStatus.FROZEN, FrozenReason.UNKNOWN),
                        StreakDay("2026-09-13", StreakDayStatus.COMPLETE),
                        StreakDay("2026-09-14", StreakDayStatus.OPEN),
                    ),
                ),
            ),
            result,
        )
        assertEquals(HttpMethod.Get, fixture.request.method)
        assertEquals("/api/v1/bonds/b1/streak", fixture.request.url.encodedPath)
    }

    @Test
    fun theRunTheLastBreakEndedAndEachFrozenReasonAreRead() = runTest {
        val fixture = Fixture {
            respondJson(
                """
                {"current":0,"longest":41,"freezesAvailable":0,"freezeProgress":0,"strictMode":false,
                 "totalCompleteDays":57,"lastCompleteDate":"2026-09-12",
                 "lastRunLength":21,"lastRunBrokenOn":"2026-09-13",
                 "days":[
                   {"date":"2026-09-11","status":"FROZEN","reason":"REST_DAY","settlesAt":null},
                   {"date":"2026-09-12","status":"FROZEN","reason":"TIMEZONE_SHIFT","settlesAt":null},
                   {"date":"2026-09-13","status":"MISSED","reason":null,"settlesAt":null},
                   {"date":"2026-09-14","status":"OPEN","reason":null,"settlesAt":"2026-09-15T11:00:00Z"}
                 ]}
                """.trimIndent(),
            )
        }

        val streak = assertIs<Result.Success<Streak>>(fixture.streak.streak("b1")).data

        assertEquals(21, streak.lastRunLength)
        assertEquals("2026-09-13", streak.lastRunBrokenOn)
        assertEquals(
            listOf(FrozenReason.REST_DAY, FrozenReason.TIMEZONE_SHIFT, null, null),
            streak.days.map { it.reason },
        )
    }

    @Test
    fun noRunHasEndedYetIsNullNotZero() = runTest {
        val fixture = Fixture {
            respondJson(
                """{"current":3,"longest":3,"freezesAvailable":0,"freezeProgress":3,"strictMode":false,
                   "totalCompleteDays":3,"lastCompleteDate":"2026-09-13","lastRunLength":null,"lastRunBrokenOn":null,
                   "days":[{"date":"2026-09-14","status":"OPEN"}]}""",
            )
        }
        val streak = assertIs<Result.Success<Streak>>(fixture.streak.streak("b1")).data
        assertNull(streak.lastRunLength)
        assertNull(streak.lastRunBrokenOn)
    }

    @Test
    fun aFrozenReasonThisBuildDoesNotKnowIsTheNeutralFallbackNotAFailure() = runTest {
        // A value added to the contract after this build: coerceInputValues makes it null, the mapper UNKNOWN.
        val fixture = Fixture {
            respondJson(
                """{"current":4,"longest":4,"freezesAvailable":1,"freezeProgress":0,"strictMode":false,
                   "totalCompleteDays":3,"lastCompleteDate":"2026-09-12",
                   "days":[{"date":"2026-09-13","status":"FROZEN","reason":"BOND_PAUSED"},{"date":"2026-09-14","status":"OPEN"}]}""",
            )
        }
        val streak = assertIs<Result.Success<Streak>>(fixture.streak.streak("b1")).data
        assertEquals(StreakDay("2026-09-13", StreakDayStatus.FROZEN, FrozenReason.UNKNOWN), streak.days.first())
    }

    @Test
    fun aReasonOnACellThatIsNotFrozenIsDropped() = runTest {
        val fixture = Fixture {
            respondJson(
                """{"current":1,"longest":1,"freezesAvailable":0,"freezeProgress":1,"strictMode":false,
                   "totalCompleteDays":1,"lastCompleteDate":"2026-09-14",
                   "days":[{"date":"2026-09-14","status":"COMPLETE","reason":"REST_DAY"}]}""",
            )
        }
        val streak = assertIs<Result.Success<Streak>>(fixture.streak.streak("b1")).data
        assertNull(streak.days.single().reason)
    }

    @Test
    fun aNewBondWithNoCompleteDayHasANullLastCompleteDate() = runTest {
        val fixture = Fixture {
            respondJson(
                """{"current":0,"longest":0,"freezesAvailable":2,"freezeProgress":0,"strictMode":true,
                   "totalCompleteDays":0,"lastCompleteDate":null,"days":[{"date":"2026-09-14","status":"OPEN"}]}""",
            )
        }
        val result = fixture.streak.streak("b1")
        assertTrue(result is Result.Success)
        assertEquals(null, result.data.lastCompleteDate)
        assertEquals(true, result.data.strictMode)
    }

    @Test
    fun aMissingNumberIsABodyThatDidNotParseNotAZero() = runTest {
        val fixture = Fixture { respondJson("""{"longest":41,"freezesAvailable":1,"strictMode":false,"days":[]}""") }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.streak.streak("b1"))
    }

    @Test
    fun aCellWithoutAStatusFailsTheStreakRatherThanBeingSkipped() = runTest {
        val fixture = Fixture {
            respondJson("""{"current":1,"longest":1,"freezesAvailable":0,"strictMode":false,"days":[{"date":"2026-09-14"}]}""")
        }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.streak.streak("b1"))
    }

    @Test
    fun anUnknownCellValueIsABodyThatDidNotParse() = runTest {
        val fixture = Fixture {
            respondJson("""{"current":1,"longest":1,"freezesAvailable":0,"strictMode":false,"days":[{"date":"2026-09-14","status":"PAUSED"}]}""")
        }
        assertEquals(Result.Failure(DataError.Network.Serialization), fixture.streak.streak("b1"))
    }

    @Test
    fun aBondTheCallerIsNotInIsTheServersAnswer() = runTest {
        val fixture = Fixture { respondProblem(HttpStatusCode.Forbidden, "NOT_A_MEMBER") }
        val result = fixture.streak.streak("b1")
        assertTrue(result is Result.Failure && (result.error as DataError.Network.Api).problem.status == 403)
    }
}
