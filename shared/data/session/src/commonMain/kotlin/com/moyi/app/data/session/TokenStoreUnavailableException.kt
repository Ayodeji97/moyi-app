package com.moyi.app.data.session

/**
 * The [SecureTokenStore] cannot be read right now, but the session in it may
 * well be intact: the iOS Keychain is locked (before the first unlock after a
 * reboot), or the Android Keystore had a transient failure. Not "no session":
 * the caller must not sign the person out, and can try again later (when the
 * app returns to the foreground, say).
 */
class TokenStoreUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
