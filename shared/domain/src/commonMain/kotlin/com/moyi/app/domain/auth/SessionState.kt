package com.moyi.app.domain.auth

/**
 * Whether someone is signed in on this device.
 *
 * [Unknown] lasts from launch until the stored session has been read; the app
 * shows nothing rather than flashing the sign-in screen at a signed-in person.
 */
sealed interface SessionState {
    data object Unknown : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val user: User) : SessionState
}

/** Something that happened to the session that the app must react to once. */
sealed interface SessionEvent {
    /** The session is over; route to sign-in and, for [SessionEndReason.ReuseDetected], explain why. */
    data class Ended(val reason: SessionEndReason) : SessionEvent
}

enum class SessionEndReason {
    /**
     * The server saw a refresh token used twice and revoked the session
     * (`401 TOKEN_REUSE_DETECTED`, ADR-0021). It may have been stolen; the
     * person is emailed and should be told why they were signed out.
     */
    ReuseDetected,

    /** The refresh token is unknown, expired or already revoked (`401 REFRESH_TOKEN_INVALID`), or could not be kept. */
    Invalid,

    /** The person signed out. */
    SignedOut,
}
