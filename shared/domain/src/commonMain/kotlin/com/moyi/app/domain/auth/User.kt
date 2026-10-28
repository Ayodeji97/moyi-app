package com.moyi.app.domain.auth

/**
 * The signed-in person, as the app needs them. Built from the server's
 * `UserResponse` by the data layer; nothing here knows the wire format.
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val status: UserStatus,
)

/** The account's lifecycle state (doc 06 §3.2). [UNKNOWN] covers a value this build has not heard of. */
enum class UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    PENDING_DELETION,
    DELETED,
    UNKNOWN,
}
