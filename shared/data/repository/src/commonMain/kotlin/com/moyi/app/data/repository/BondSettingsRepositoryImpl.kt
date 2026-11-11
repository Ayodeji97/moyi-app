package com.moyi.app.data.repository

import com.moyi.app.api.apis.BondDeletionControllerApi
import com.moyi.app.api.apis.BondMemberSettingsControllerApi
import com.moyi.app.api.apis.BondTimezoneControllerApi
import com.moyi.app.api.apis.BondsControllerApi
import com.moyi.app.api.models.BondResponse
import com.moyi.app.api.models.ChangeTimezoneRequest
import com.moyi.app.api.models.ConfirmTimezoneRequest
import com.moyi.app.api.models.EndBondRequest
import com.moyi.app.api.models.RequiredEndBondRequest
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
import com.moyi.app.domain.bond.PendingDeletionRequest
import com.moyi.app.domain.bond.PendingTimezoneChange
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
    private val timezoneApi = BondTimezoneControllerApi(config.origin, authedClient.client)
    private val deletionApi = BondDeletionControllerApi(config.origin, authedClient.client)

    override suspend fun settings(bondId: String): Result<BondSettings, DataError.Network> =
        safeApiCallWithETag { bondsApi.getBond(bondId) }.mapOrSerialization { (body, etag) -> body.toSettings(etag) }

    override suspend fun update(bondId: String, version: String, change: BondSettingsChange): Result<BondSettings, DataError.Network> {
        val request = PatchBondRequest(
            name = change.name,
            type = change.type?.toPatchRequestType(),
            revealTimeLocal = change.revealTime?.toString(),
            strictMode = change.strictMode,
        )
        return safeApiCallWithETag { bondsApi.patchBond(bondId, ifMatch = version, patchBondRequest = request) }
            .mapOrSerialization { (body, etag) -> body.toSettings(etag) }
    }

    // Always an explicit true or false, never an empty body: the two routes read an absent
    // value in opposite ways (keep on leave, withdraw on block, doc 06 §3.3), and the owner
    // ruled that block is to require it (2026-10-21). What is erased must never hang on a default.
    override suspend fun leave(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network> =
        safeApiCallEmpty { bondsApi.leaveBond(bondId, EndBondRequest(withdrawEntries = withdrawEntries)) }

    override suspend fun block(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network> =
        safeApiCallEmpty { bondsApi.blockBond(bondId, RequiredEndBondRequest(withdrawEntries = withdrawEntries)) }

    // A 202 that carries the bond and its new ETag, as every bond response does.
    override suspend fun requestDeletion(bondId: String): Result<BondSettings, DataError.Network> =
        safeApiCallWithETag { deletionApi.requestBondDeletion(bondId) }.mapOrSerialization { (body, etag) -> body.toSettings(etag) }

    override suspend fun cancelDeletion(bondId: String): EmptyResult<DataError.Network> =
        safeApiCallEmpty { deletionApi.cancelBondDeletion(bondId) }

    // The two-party flow takes no If-Match (ADR-0030), but its 200s carry the new ETag,
    // which the next PATCH /bonds/{id} needs.
    override suspend fun proposeTimezone(bondId: String, timezone: String): Result<BondSettings, DataError.Network> =
        safeApiCallWithETag { timezoneApi.proposeBondTimezone(bondId, ChangeTimezoneRequest(anchorTimezone = timezone)) }
            .mapOrSerialization { (body, etag) -> body.toSettings(etag) }

    override suspend fun confirmTimezone(bondId: String, proposalId: String): Result<BondSettings, DataError.Network> =
        safeApiCallWithETag { timezoneApi.confirmBondTimezone(bondId, ConfirmTimezoneRequest(proposalId = proposalId)) }
            .mapOrSerialization { (body, etag) -> body.toSettings(etag) }

    override suspend fun cancelTimezoneChange(bondId: String): EmptyResult<DataError.Network> =
        safeApiCallEmpty { timezoneApi.cancelBondTimezoneChange(bondId) }

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

/**
 * The contract makes every field the settings screen shows required, so the body
 * fails to decode without one. What is still checked here: a reveal time that is not
 * `HH:mm` is malformed, not absent, and a response without an ETag has no version.
 */
internal fun BondResponse.toSettings(etag: String?): BondSettings? = BondSettings(
    bondId = id,
    name = name,
    type = BondType.valueOf(type.name),
    status = BondStatus.valueOf(status.name),
    anchorTimezone = anchorTimezone,
    revealTime = revealTimeLocal?.let { TimeOfDay.parse(it) ?: return null },
    strictMode = strictMode,
    version = etag ?: return null,
    pendingTimezone = pendingTimezoneChange?.let { pending ->
        PendingTimezoneChange(
            id = pending.id,
            timezone = pending.proposedTimezone,
            proposedByMe = pending.proposedByMemberId == me.memberId,
            expiresAt = pending.expiresAt,
        )
    },
    invite = invite?.toInvite(),
    partnerName = partnerName(members, me.memberId),
    pendingDeletion = pendingDeletionRequest?.let { pending ->
        PendingDeletionRequest(
            requestedByMe = pending.requestedByMemberId == me.memberId,
            expiresAt = pending.expiresAt,
        )
    },
    deletionScheduledFor = deletionScheduledFor,
    timezoneChangeAllowedFrom = timezoneChangeAllowedFrom?.toString(),
    myName = members.orEmpty().firstOrNull { it.id == me.memberId }?.displayName,
    createdAt = createdAt,
)

private fun MemberSettingsResponse.toDomain(): MemberSettings? = MemberSettings(
    reminderTime = TimeOfDay.parse(reminderTimeLocal) ?: return null,
    reminderTimezone = reminderTimezone,
    quietHoursStart = quietHoursStart?.let { TimeOfDay.parse(it) ?: return null },
    quietHoursEnd = quietHoursEnd?.let { TimeOfDay.parse(it) ?: return null },
    nicknameForOther = nicknameForOther,
)
