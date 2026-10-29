package com.moyi.app.data.repository

import com.moyi.app.api.apis.BondInvitesControllerApi
import com.moyi.app.api.apis.BondsControllerApi
import com.moyi.app.api.apis.InvitesControllerApi
import com.moyi.app.api.models.CreateBondRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.data.network.safeApiCallEmpty
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview

/**
 * [BondRepository] over the generated bond and invite APIs, on the AUTHED
 * client: a `401` here is an expired access token, which the client refreshes
 * once (single flight, SessionManager) and retries. A refresh that fails ends
 * the session, and the shell routes to sign in.
 */
class BondRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : BondRepository {
    private val bondsApi = BondsControllerApi(config.origin, authedClient.client)
    private val bondInvitesApi = BondInvitesControllerApi(config.origin, authedClient.client)
    private val invitesApi = InvitesControllerApi(config.origin, authedClient.client)

    override suspend fun bonds(): Result<List<Bond>, DataError.Network> =
        safeApiCall { bondsApi.listBonds() }.mapOrSerialization { response ->
            // One malformed bond fails the list: routing on a partial list could send a
            // person with a bond back through onboarding.
            response.bonds?.map { it.toBond() ?: return@mapOrSerialization null }
        }

    override suspend fun createBond(name: String, type: BondType, anchorTimezone: String): Result<Bond, DataError.Network> {
        val request = CreateBondRequest(
            anchorTimezone = anchorTimezone,
            name = name,
            // A free string in the contract, not an enum (docs/backend-contract-gaps.md, row 14).
            type = type.name,
            // The creator's own reminders follow their device's zone from day one (ADR-0026).
            reminderTimezone = anchorTimezone,
        )
        return safeApiCall { bondsApi.createBond(request) }.mapOrSerialization { it.toBond() }
    }

    override suspend fun createInvite(bondId: String): Result<Invite, DataError.Network> =
        safeApiCall { bondInvitesApi.createBondInvite(bondId) }.mapOrSerialization { it.toInvite() }

    override suspend fun revokeInvite(bondId: String, inviteId: String): EmptyResult<DataError.Network> =
        safeApiCallEmpty { bondInvitesApi.revokeBondInvite(bondId, inviteId) }

    override suspend fun resolveInvite(code: String): Result<InvitePreview, DataError.Network> =
        safeApiCall { invitesApi.resolve(code) }.mapOrSerialization { it.toPreview() }

    override suspend fun acceptInvite(code: String): Result<Bond, DataError.Network> =
        safeApiCall { invitesApi.accept(code) }.mapOrSerialization { it.toBond() }
}
