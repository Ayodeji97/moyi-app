package com.moyi.app.data.session

import com.moyi.app.api.models.LoginResponse
import com.moyi.app.api.models.UserResponse
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus

/** A new token pair and the user it belongs to: what login and refresh both answer. */
class SessionGrant(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
) {
    override fun toString(): String = "SessionGrant(accessToken=<redacted>, refreshToken=<redacted>, user=${user.id})"
}

/**
 * The generated `LoginResponse` declares no required fields (contract gaps 4
 * and 5), so every field arrives nullable. A 2xx without a token or a user is
 * not a session: it is [DataError.Network.Serialization], the same as a body
 * that did not parse.
 */
fun LoginResponse.toSessionGrant(): Result<SessionGrant, DataError.Network> {
    val access = accessToken?.takeIf { it.isNotBlank() }
    val refresh = refreshToken?.takeIf { it.isNotBlank() }
    val mappedUser = user?.toUser()
    return if (access == null || refresh == null || mappedUser == null) {
        Result.Failure(DataError.Network.Serialization)
    } else {
        Result.Success(SessionGrant(access, refresh, mappedUser))
    }
}

/** Null when a field the app cannot do without (id, email, display name) is missing. */
fun UserResponse.toUser(): User? = User(
    id = id ?: return null,
    email = email ?: return null,
    displayName = displayName ?: return null,
    emailVerified = emailVerified ?: false,
    status = status?.name.toUserStatus(),
)

internal fun String?.toUserStatus(): UserStatus =
    UserStatus.entries.find { it != UserStatus.UNKNOWN && it.name == this } ?: UserStatus.UNKNOWN

internal fun SessionGrant.toStoredSession(): StoredSession = StoredSession(
    refreshToken = refreshToken,
    userId = user.id,
    email = user.email,
    displayName = user.displayName,
    emailVerified = user.emailVerified,
    status = user.status.name,
)

internal fun StoredSession.toUser(): User = User(
    id = userId,
    email = email,
    displayName = displayName,
    emailVerified = emailVerified,
    status = status.toUserStatus(),
)
