package com.moyi.app.domain.bond

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result

/**
 * Bonds and their invites (doc 06 §3.3). Every call needs a signed-in person.
 *
 * Refusals carry the server's code in [DataError.Network.Api]; the caller
 * switches on it. The ones onboarding acts on:
 * - `403 EMAIL_NOT_VERIFIED` from create and accept (FR-002): the unverified gate.
 * - `404 INVITE_NOT_USABLE` from resolve and accept: one answer for every way a
 *   code fails (expired, revoked, used, full, unknown, blocked; FR-024).
 * - `409 ALREADY_MEMBER` from accept, `409 BOND_LIMIT_REACHED` from create and accept.
 */
interface BondRepository {
    /** The person's bonds, newest first, ended ones included. */
    suspend fun bonds(): Result<List<Bond>, DataError.Network>

    /**
     * Creates a bond with its first invite. [anchorTimezone] is an IANA zone id;
     * it is also sent as the creator's reminder zone (ADR-0026).
     */
    suspend fun createBond(name: String, type: BondType, anchorTimezone: String): Result<Bond, DataError.Network>

    /** A new invite for [bondId]. Revokes any outstanding one: there is only ever one live code. */
    suspend fun createInvite(bondId: String): Result<Invite, DataError.Network>

    /** Revokes an invite ("Cancel this invite", states.md §2). */
    suspend fun revokeInvite(bondId: String, inviteId: String): EmptyResult<DataError.Network>

    /** Who invited the person, and to what, before the invite is spent. [code] is already normalised. */
    suspend fun resolveInvite(code: String): Result<InvitePreview, DataError.Network>

    /** Joins with [code]. Spends the invite (single use, FR-023). */
    suspend fun acceptInvite(code: String): Result<Bond, DataError.Network>
}
