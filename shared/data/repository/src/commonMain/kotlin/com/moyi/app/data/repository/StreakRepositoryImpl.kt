package com.moyi.app.data.repository

import com.moyi.app.api.apis.StreakControllerApi
import com.moyi.app.api.models.StreakDayResponse
import com.moyi.app.api.models.StreakResponse
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.domain.streak.FrozenReason
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
 * The contract now marks every field `required`, so a missing number or cell fails in the
 * decoder (`Serialization`) and never reaches this function. What is left to decide here is
 * the meaning of the two optional parts:
 *
 * - `lastRunLength` and `lastRunBrokenOn` are passed through as sent (both null until a run
 *   has ended).
 * - A cell's `reason` is kept only on a `FROZEN` cell. A `FROZEN` cell with no reason is
 *   [FrozenReason.UNKNOWN]: `MoyiJson`'s `coerceInputValues` turns a reason this build does
 *   not know (added to the contract later) into null rather than failing the whole body, and
 *   the screen then says nothing about why rather than guessing.
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
    lastRunLength = lastRunLength,
    lastRunBrokenOn = lastRunBrokenOn?.toString(),
)

internal fun StreakDayResponse.toStreakDay(): StreakDay {
    val status = when (status) {
        StreakDayResponse.Status.COMPLETE -> StreakDayStatus.COMPLETE
        StreakDayResponse.Status.FROZEN -> StreakDayStatus.FROZEN
        StreakDayResponse.Status.SOLO -> StreakDayStatus.SOLO
        StreakDayResponse.Status.MISSED -> StreakDayStatus.MISSED
        StreakDayResponse.Status.OPEN -> StreakDayStatus.OPEN
    }
    val frozenReason = if (status != StreakDayStatus.FROZEN) {
        null
    } else {
        when (reason) {
            StreakDayResponse.Reason.REST_DAY -> FrozenReason.REST_DAY
            StreakDayResponse.Reason.TIMEZONE_SHIFT -> FrozenReason.TIMEZONE_SHIFT
            null -> FrozenReason.UNKNOWN
        }
    }
    return StreakDay(date = date.toString(), status = status, reason = frozenReason)
}
