package com.moyi.app.domain.auth

import com.moyi.app.core.DataError
import com.moyi.app.core.Result

/**
 * The signed-in person's own account, read fresh from the server.
 *
 * The session keeps the [User] it was signed in with, so its `emailVerified`
 * is as old as the sign-in. Whether someone may create or join a bond
 * (FR-002) is read from here instead: `GET /me` is the call behind "check
 * again" once the app has signed in (doc 06 §3.2).
 */
interface AccountRepository {
    suspend fun currentUser(): Result<User, DataError.Network>
}
