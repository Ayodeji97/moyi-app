package com.moyi.app.data.repository

import com.moyi.app.api.models.BondResponse
import com.moyi.app.api.models.CreateBondRequest
import com.moyi.app.api.models.InviteDetailResponse
import com.moyi.app.api.models.InvitePreviewResponse
import com.moyi.app.api.models.InviteResponse
import com.moyi.app.api.models.MemberResponse
import com.moyi.app.api.models.PatchBondRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview

// The generated bond and invite models declare their fields `required` and
// non-null (PR #52 synced the contract that says so), so a 2xx missing one fails
// to decode as [DataError.Network.Serialization] before it reaches a mapper.

internal fun BondResponse.toBond(): Bond = Bond(
    id = id,
    name = name,
    type = type.toDomain(),
    status = status.toDomain(),
    invite = invite?.toInvite(),
    partnerName = partnerName(members, me.memberId),
    anchorTimezone = anchorTimezone,
    activeMembers = activeMembers(members, me.memberId),
)

/**
 * How many members are still in the bond (no `leftAt`), the caller included. Null when the
 * list does not show the caller as one of them: the app then cannot say who is in it, and
 * ending a bond "nobody has joined" must not act on a guess.
 */
internal fun activeMembers(members: List<MemberResponse>?, myMemberId: String?): Int? {
    val active = members.orEmpty().filter { it.leftAt == null }
    if (myMemberId == null || active.none { it.id == myMemberId }) return null
    return active.size
}

/**
 * The other member's name: the first member who is not [myMemberId], one still in
 * the bond before one who has left. Null when nobody else is listed, and when
 * [myMemberId] is missing: without it the app cannot tell which member is which,
 * and naming the caller as their own partner would be worse than naming no one.
 */
internal fun partnerName(members: List<MemberResponse>?, myMemberId: String?): String? {
    if (myMemberId == null) return null
    return members.orEmpty()
        .filter { it.id != myMemberId }
        .sortedBy { it.leftAt != null }
        .firstOrNull()
        ?.displayName
}

internal fun InviteResponse.toInvite(): Invite = Invite(
    id = id,
    code = code,
    link = link,
    expiresAt = expiresAt,
)

internal fun InviteDetailResponse.toInvite(): Invite = Invite(
    id = id,
    code = code,
    link = link,
    expiresAt = expiresAt,
)

/**
 * The confirm screen names the inviter and the bond. The backend answers "Someone"
 * for an inviter it no longer knows, so the name is always there.
 */
internal fun InvitePreviewResponse.toPreview(): InvitePreview = InvitePreview(
    inviterName = inviterDisplayName,
    bondName = bondName,
    bondType = BondType.valueOf(bondType.name),
)

private fun BondResponse.Type.toDomain(): BondType = BondType.valueOf(name)

// Spelled out rather than `valueOf(name)`: the compiler checks every type is sent,
// and a renamed constant on either side fails the build, not a request.
internal fun BondType.toCreateRequestType(): CreateBondRequest.Type = when (this) {
    BondType.COUPLE -> CreateBondRequest.Type.COUPLE
    BondType.FRIENDS -> CreateBondRequest.Type.FRIENDS
    BondType.FAMILY -> CreateBondRequest.Type.FAMILY
    BondType.PARENT_CHILD -> CreateBondRequest.Type.PARENT_CHILD
}

internal fun BondType.toPatchRequestType(): PatchBondRequest.Type = when (this) {
    BondType.COUPLE -> PatchBondRequest.Type.COUPLE
    BondType.FRIENDS -> PatchBondRequest.Type.FRIENDS
    BondType.FAMILY -> PatchBondRequest.Type.FAMILY
    BondType.PARENT_CHILD -> PatchBondRequest.Type.PARENT_CHILD
}

private fun BondResponse.Status.toDomain(): BondStatus = BondStatus.valueOf(name)

/** Maps a decoded body the mapper may still reject (null), turning that into [DataError.Network.Serialization]. */
internal inline fun <T, R> Result<T, DataError.Network>.mapOrSerialization(transform: (T) -> R?): Result<R, DataError.Network> =
    when (this) {
        is Result.Failure -> this
        is Result.Success -> transform(data)?.let { Result.Success(it) } ?: Result.Failure(DataError.Network.Serialization)
    }
