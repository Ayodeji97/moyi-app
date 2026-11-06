package com.moyi.app.data.repository

import com.moyi.app.api.apis.StreakControllerApi
import com.moyi.app.api.models.StreakDayResponse
import com.moyi.app.api.models.StreakResponse
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
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
        safeApiCall { api.streak(bondId) }.mapOrSerialization { it.toStreak() }
}

/**
 * The contract declares no `required` on `StreakResponse` (docs/backend-contract-gaps.md,
 * row 15), so every field is nullable in the generated type. The numbers and the cells are
 * what the screen is about: a response missing any of them is unreadable (null, so a
 * Serialization failure), never drawn as zero. `freezeProgress`, `totalCompleteDays` and
 * `lastCompleteDate` are not drawn, so they are allowed to be absent.
 */
internal fun StreakResponse.toStreak(): Streak? {
    val cells = days?.map { it.toStreakDay() ?: return null } ?: return null
    return Streak(
        current = current ?: return null,
        longest = longest ?: return null,
        freezesAvailable = freezesAvailable ?: return null,
        freezeProgress = freezeProgress ?: 0,
        strictMode = strictMode ?: return null,
        totalCompleteDays = totalCompleteDays ?: 0,
        lastCompleteDate = lastCompleteDate?.toString(),
        days = cells,
    )
}

private fun StreakDayResponse.toStreakDay(): StreakDay? = StreakDay(
    date = (date ?: return null).toString(),
    status = when (status ?: return null) {
        StreakDayResponse.Status.COMPLETE -> StreakDayStatus.COMPLETE
        StreakDayResponse.Status.FROZEN -> StreakDayStatus.FROZEN
        StreakDayResponse.Status.SOLO -> StreakDayStatus.SOLO
        StreakDayResponse.Status.MISSED -> StreakDayStatus.MISSED
        StreakDayResponse.Status.OPEN -> StreakDayStatus.OPEN
    },
)
