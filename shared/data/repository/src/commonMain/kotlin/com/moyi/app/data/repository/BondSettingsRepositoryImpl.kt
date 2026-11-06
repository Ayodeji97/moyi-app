package com.moyi.app.data.repository

import com.moyi.app.api.apis.BondMemberSettingsControllerApi
import com.moyi.app.api.apis.BondsControllerApi
import com.moyi.app.api.models.BondResponse
import com.moyi.app.api.models.EndBondRequest
import com.moyi.app.api.models.MemberSettingsRequest
import com.moyi.app.api.models.MemberSettingsResponse
import com.moyi.app.api.models.PatchBondRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.network.safeApiCallEmpty
import com.moyi.app.data.network.safeApiCallWithETag
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.TimeOfDay

/**
 * [BondSettingsRepository] over the generated bond and member-settings APIs, on the
 * AUTHED client (a `401` refreshes once and retries, as everywhere else).
 *
 * The Bond's settings are edited conditionally: the `ETag` of every read and every
 * `200` is kept with the settings as [BondSettings.version] and sent back as `If-Match`.
 * A `2xx` without one is treated as a body that did not parse: without it no change
 * could ever be sent, and guessing one (or sending `*`, which the server refuses)
 * would defeat the check.
 */
class BondSettingsRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : BondSettingsRepository {
    private val bondsApi = BondsControllerApi(config.origin, authedClient.client)
    private val memberSettingsApi = BondMemberSettingsControllerApi(config.origin, authedClient.client)

    override suspend fun settings(bondId: String): Result<BondSettings, DataError.Network> =
        safeApiCallWithETag { bondsApi.getBond(bondId) }.mapOrSerialization { (body, etag) -> body.toSettings(etag) }

    override suspend fun update(bondId: String, version: String, change: BondSettingsChange): Result<BondSettings, DataError.Network> {
        val request = PatchBondRequest(
            name = change.name,
            // A free string in the contract, not an enum (docs/backend-contract-gaps.md, row 14).
            type = change.type?.name,
            revealTimeLocal = change.revealTime?.toString(),
            strictMode = change.strictMode,
        )
        return safeApiCallWithETag { bondsApi.patchBond(bondId, ifMatch = version, patchBondRequest = request) }
            .mapOrSerialization { (body, etag) -> body.toSettings(etag) }
    }

    // An explicit false, not an empty body: what is kept should not depend on a default
    // (the same body on the block route means the opposite, doc 06 §3.3).
    override suspend fun leave(bondId: String): EmptyResult<DataError.Network> =
        safeApiCallEmpty { bondsApi.leaveBond(bondId, EndBondRequest(withdrawEntries = false)) }

    override suspend fun memberSettings(bondId: String): Result<MemberSettings, DataError.Network> =
        safeApiCall { memberSettingsApi.getMemberSettings(bondId) }.mapOrSerialization { it.toDomain() }

    override suspend fun replaceMemberSettings(bondId: String, settings: MemberSettings): Result<MemberSettings, DataError.Network> {
        val request = MemberSettingsRequest(
            reminderTimeLocal = settings.reminderTime.toString(),
            reminderTimezone = settings.reminderTimezone,
            quietHoursStart = settings.quietHoursStart?.toString(),
            quietHoursEnd = settings.quietHoursEnd?.toString(),
            nicknameForOther = settings.nicknameForOther,
        )
        return safeApiCall { memberSettingsApi.replaceMemberSettings(bondId, request) }.mapOrSerialization { it.toDomain() }
    }
}

/** Every field the settings screen shows is required; a reveal time that is not `HH:mm` is malformed, not absent. */
internal fun BondResponse.toSettings(etag: String?): BondSettings? = BondSettings(
    bondId = id ?: return null,
    name = name ?: return null,
    type = type?.let { BondType.valueOf(it.name) } ?: return null,
    status = status?.let { BondStatus.valueOf(it.name) } ?: return null,
    anchorTimezone = anchorTimezone ?: return null,
    revealTime = revealTimeLocal?.let { TimeOfDay.parse(it) ?: return null },
    strictMode = strictMode ?: return null,
    version = etag ?: return null,
)

private fun MemberSettingsResponse.toDomain(): MemberSettings? = MemberSettings(
    reminderTime = reminderTimeLocal?.let(TimeOfDay::parse) ?: return null,
    reminderTimezone = reminderTimezone ?: return null,
    quietHoursStart = quietHoursStart?.let { TimeOfDay.parse(it) ?: return null },
    quietHoursEnd = quietHoursEnd?.let { TimeOfDay.parse(it) ?: return null },
    nicknameForOther = nicknameForOther,
)
