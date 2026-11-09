package com.moyi.app.data.repository

import com.moyi.app.api.apis.StreakControllerApi
import com.moyi.app.api.models.StreakDayResponse
import com.moyi.app.api.models.StreakResponse
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.core.map
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.domain.streak.Streak
import com.moyi.app.domain.streak.StreakDay
import com.moyi.app.domain.streak.StreakDayStatus
import com.moyi.app.domain.streak.StreakRepository

/**
 * [StreakRepository] over the generated `StreakControllerApi`, on the AUTHED client.
 * Online only, like the Archive: no cache yet (doc 13 §5).
 */
class StreakRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : StreakRepository {
    private val api = StreakControllerApi(config.origin, authedClient.client)

    override suspend fun streak(bondId: String): Result<Streak, DataError.Network> =
        safeApiCall { api.streak(bondId) }.map { it.toStreak() }
}

/**
 * The contract marks `StreakResponse`'s numbers and cells required, so a response missing
 * one fails to decode (a Serialization failure), never drawn as zero. Only
 * `lastCompleteDate` may be absent: there is none before the first complete day.
 */
internal fun StreakResponse.toStreak(): Streak = Streak(
    current = current,
    longest = longest,
    freezesAvailable = freezesAvailable,
    freezeProgress = freezeProgress,
    strictMode = strictMode,
    totalCompleteDays = totalCompleteDays,
    lastCompleteDate = lastCompleteDate?.toString(),
    days = days.map { it.toStreakDay() },
)

private fun StreakDayResponse.toStreakDay(): StreakDay = StreakDay(
    date = date.toString(),
    status = when (status) {
        StreakDayResponse.Status.COMPLETE -> StreakDayStatus.COMPLETE
        StreakDayResponse.Status.FROZEN -> StreakDayStatus.FROZEN
        StreakDayResponse.Status.SOLO -> StreakDayStatus.SOLO
        StreakDayResponse.Status.MISSED -> StreakDayStatus.MISSED
        StreakDayResponse.Status.OPEN -> StreakDayStatus.OPEN
    },
)
