package com.moyi.app.domain.account

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import kotlin.time.Instant

/**
 * A pending account deletion (FR-008, ADR-0058). Nothing is destroyed when it is asked for:
 * the person can sign in and call it off until [cancellableUntil] (14 days after
 * [requestedAt]); within the hour after that the account is deleted. [deleteBy] is FR-008's
 * outer bound (30 days), never earlier than [cancellableUntil].
 */
data class DeletionRequest(
    val requestedAt: Instant,
    val cancellableUntil: Instant,
    val deleteBy: Instant,
)

/**
 * Account deletion, `POST` / `DELETE /me/deletion-request`, and the pending request `GET /me`
 * carries (so a reinstall still shows it).
 *
 * Every failure is a [DataError.Network]. On [request], a wrong password is
 * [DataError.Network.Api] `403 INVALID_CREDENTIALS`: the session is fine; only a `401`
 * is a refused token. On [cancel], `404` means there is nothing left
 * that can be called off.
 */
interface AccountDeletionRepository {
    /** The deletion waiting to be carried out, or null when none is. */
    suspend fun pending(): Result<DeletionRequest?, DataError.Network>

    /** Asks for the account to be deleted, with the password typed again. The same request comes back when one is pending. */
    suspend fun request(password: String): Result<DeletionRequest, DataError.Network>

    suspend fun cancel(): EmptyResult<DataError.Network>
}
