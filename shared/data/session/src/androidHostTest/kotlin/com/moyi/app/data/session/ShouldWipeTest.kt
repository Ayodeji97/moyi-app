package com.moyi.app.data.session

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which read failures delete the stored session. Wiping is final (the person
 * is signed out), so only failures that prove the key or the payload is gone
 * may do it; a Keystore hiccup must leave the session for a retry.
 *
 * KeyPermanentlyInvalidatedException is in the wipe list too, but it is an
 * android.jar stub that throws "Stub!" when constructed on the host, so it is
 * not exercised here.
 */
class ShouldWipeTest {
    @Test
    fun aTamperedOrForeignCiphertextWipes() = assertTrue(shouldWipe(AEADBadTagException("tag mismatch")))

    @Test
    fun anUnrecoverableKeyWipes() = assertTrue(shouldWipe(UnrecoverableKeyException("gone")))

    @Test
    fun aMissingKeyWipes() = assertTrue(shouldWipe(SessionKeyMissingException()))

    @Test
    fun aMalformedFileWipes() = assertTrue(shouldWipe(IllegalArgumentException("Malformed session file")))

    @Test
    fun anUndecodablePayloadWipes() = assertTrue(shouldWipe(SerializationException("bad json")))

    @Test
    fun aProviderFailureKeepsTheSession() = assertFalse(shouldWipe(ProviderException("Keystore operation failed")))

    @Test
    fun aKeyStoreFailureKeepsTheSession() = assertFalse(shouldWipe(KeyStoreException("keystore2 busy")))

    @Test
    fun anIoFailureKeepsTheSession() = assertFalse(shouldWipe(IOException("read failed")))

    @Test
    fun anythingUnforeseenKeepsTheSession() = assertFalse(shouldWipe(IllegalStateException("?")))
}
