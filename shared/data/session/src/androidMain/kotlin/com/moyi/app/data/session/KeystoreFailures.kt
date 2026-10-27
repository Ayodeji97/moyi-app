package com.moyi.app.data.session

import android.security.keystore.KeyPermanentlyInvalidatedException
import kotlinx.serialization.SerializationException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException

/**
 * Whether a failure reading the stored session proves the session is gone for
 * good, so the file and key should be deleted (which signs the person out).
 *
 * Yes only when the KEY or the PAYLOAD is gone or wrong:
 * - [AEADBadTagException]: the ciphertext does not authenticate under this
 *   key (tampered, restored from elsewhere, or the key was replaced).
 * - [KeyPermanentlyInvalidatedException]: the Keystore invalidated the key.
 * - [UnrecoverableKeyException]: the key exists but cannot be recovered.
 * - [SessionKeyMissingException]: the file is there, its key is not.
 * - [IllegalArgumentException] (the format and length checks) and
 *   [SerializationException]: the bytes are not a session this build reads.
 *
 * Everything else ([java.security.ProviderException],
 * [java.security.KeyStoreException], [java.io.IOException], and anything not
 * foreseen) may be transient: keystore2 busy, a TEE hiccup, a read that
 * failed. Wiping then would sign someone out over a glitch, so the session is
 * kept and the caller gets [TokenStoreUnavailableException] to retry.
 */
internal fun shouldWipe(failure: Throwable): Boolean = when (failure) {
    is AEADBadTagException,
    is KeyPermanentlyInvalidatedException,
    is UnrecoverableKeyException,
    is SessionKeyMissingException,
    is SerializationException,
    is IllegalArgumentException,
    -> true
    else -> false
}

/** The session file exists but the Keystore has no key under its alias. */
internal class SessionKeyMissingException : Exception("Session key missing")
