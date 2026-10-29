package com.moyi.app.data.repository

import com.moyi.app.api.models.BondResponse
import com.moyi.app.api.models.InviteDetailResponse
import com.moyi.app.api.models.InvitePreviewResponse
import com.moyi.app.api.models.InviteResponse
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview

// The generated bond and invite models declare no `required` fields
// (docs/backend-contract-gaps.md, row 13), so every field arrives nullable. A
// 2xx missing a field the app routes on is treated as a body that did not
// parse, [DataError.Network.Serialization], never filled in with a guess.

internal fun BondResponse.toBond(): Bond? = Bond(
    id = id ?: return null,
    name = name ?: return null,
    type = type?.toDomain() ?: return null,
    status = status?.toDomain() ?: return null,
    // An invite missing its code or link is no invite the person can pass on.
    invite = invite?.toInvite(),
)

internal fun InviteResponse.toInvite(): Invite? = Invite(
    id = id ?: return null,
    code = code ?: return null,
    link = link ?: return null,
)

internal fun InviteDetailResponse.toInvite(): Invite? = Invite(
    id = id ?: return null,
    code = code ?: return null,
    link = link ?: return null,
)

/**
 * All three are needed: the confirm screen names the inviter and the bond.
 * The backend already answers "Someone" for an inviter it no longer knows, so
 * an absent name is a malformed body, not a deleted account.
 */
internal fun InvitePreviewResponse.toPreview(): InvitePreview? = InvitePreview(
    inviterName = inviterDisplayName ?: return null,
    bondName = bondName ?: return null,
    bondType = bondType?.let { BondType.valueOf(it.name) } ?: return null,
)

private fun BondResponse.Type.toDomain(): BondType = BondType.valueOf(name)

private fun BondResponse.Status.toDomain(): BondStatus = BondStatus.valueOf(name)

/** Maps a decoded body, turning a missing required field into [DataError.Network.Serialization]. */
internal inline fun <T, R> Result<T, DataError.Network>.mapOrSerialization(transform: (T) -> R?): Result<R, DataError.Network> =
    when (this) {
        is Result.Failure -> this
        is Result.Success -> transform(data)?.let { Result.Success(it) } ?: Result.Failure(DataError.Network.Serialization)
    }
