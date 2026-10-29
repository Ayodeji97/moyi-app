package com.moyi.app.debug

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * DEBUG ONLY. The bond repository the onboarding preview index runs on
 * (`-PmoyiUiPreview=onboarding`), so its screens can be clicked through with
 * no backend and no session. Bound over the real one only in that build
 * (KoinInit); nothing else ever sees it.
 *
 * Every well-formed code resolves to the same inviter and bond, except
 * [UNUSABLE_CODE], which plays "cannot be used". Creating always succeeds
 * with [INVITE_CODE].
 */
internal class PreviewBondRepository : BondRepository {
    private val invite = Invite(id = "preview-invite", code = INVITE_CODE, link = "https://getmoyi.app/i/$INVITE_CODE")

    override suspend fun bonds(): Result<List<Bond>, DataError.Network> = Result.Success(emptyList())

    override suspend fun createBond(name: String, type: BondType, anchorTimezone: String): Result<Bond, DataError.Network> =
        Result.Success(Bond(id = "preview-bond", name = name, type = type, status = BondStatus.PENDING_MEMBER, invite = invite))

    override suspend fun createInvite(bondId: String): Result<Invite, DataError.Network> = Result.Success(invite)

    override suspend fun revokeInvite(bondId: String, inviteId: String): EmptyResult<DataError.Network> = Result.Success(Unit)

    override suspend fun resolveInvite(code: String): Result<InvitePreview, DataError.Network> =
        if (code == UNUSABLE_CODE) notUsable else Result.Success(InvitePreview("Amara", "Amara and Tunde", BondType.COUPLE))

    override suspend fun acceptInvite(code: String): Result<Bond, DataError.Network> =
        if (code == UNUSABLE_CODE) {
            notUsable
        } else {
            Result.Success(Bond(id = "preview-bond", name = "Amara and Tunde", type = BondType.COUPLE, status = BondStatus.ACTIVE, invite = null))
        }

    private val notUsable = Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.INVITE_NOT_USABLE)))

    companion object {
        /** A code in the invite alphabet (`23456789ABCDEFGHJKMNPQRSTVWXYZ`). */
        const val INVITE_CODE = "K7Q2PD"
        const val UNUSABLE_CODE = "ZZZZZZ"
    }
}

/** Loaded after [appModules], so its binding replaces the real repository. */
internal val previewDataModule: Module = module {
    single<BondRepository> { PreviewBondRepository() }
}
