package com.moyi.app.data.session

/**
 * Keeps the session across launches: the refresh token and enough of the
 * user to show who is signed in before the network answers.
 *
 * Implementations encrypt at rest and stay on this device: the iOS Keychain
 * (`KeychainTokenStore`) and an Android-Keystore-encrypted file
 * (`AndroidKeystoreTokenStore`). Never DataStore or preferences (CLAUDE.md).
 * The access token is never stored; it lives only in memory.
 */
interface SecureTokenStore {
    /**
     * The stored session, or null if there is none or it is gone for good
     * (unreadable, its key lost; that signs the person out).
     *
     * @throws TokenStoreUnavailableException when the store cannot be read
     *   RIGHT NOW but the session may be intact (a locked Keychain, a
     *   transient Keystore failure). The caller must not treat that as signed
     *   out; it can try again later.
     */
    suspend fun read(): StoredSession?

    /**
     * Replaces the stored session. Throws if it could not be written: the
     * caller must know, because a rotated refresh token that was not kept is
     * a session that cannot be refreshed again.
     */
    suspend fun write(session: StoredSession)

    suspend fun clear()
}

/**
 * What [SecureTokenStore] keeps. [toString] never shows [refreshToken]: this
 * object can end up in a log line, a crash report or a test failure message.
 */
data class StoredSession(
    val refreshToken: String,
    val userId: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val status: String,
) {
    override fun toString(): String =
        "StoredSession(refreshToken=<redacted>, userId=$userId, emailVerified=$emailVerified, status=$status)"
}
