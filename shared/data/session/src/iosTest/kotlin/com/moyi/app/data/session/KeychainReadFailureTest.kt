@file:OptIn(BetaInteropApi::class)

package com.moyi.app.data.session

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.errSecInteractionNotAllowed
import platform.Security.errSecItemNotFound
import platform.Security.errSecNotAvailable
import platform.Security.errSecSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The real Keychain cannot be exercised here (unsigned simulator tests get
 * -34018), so the status mapping is tested on its own.
 */
class KeychainReadFailureTest {
    private fun utf8(text: String): NSData = NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding)!!

    @Test
    fun aLockedKeychainIsUnavailableNotSignedOut() {
        assertEquals(KeychainReadOutcome.Unavailable(errSecInteractionNotAllowed), keychainReadOutcome(errSecInteractionNotAllowed, null))
    }

    @Test
    fun aKeychainThatIsNotAvailableRightNowIsUnavailableNotSignedOut() {
        assertEquals(KeychainReadOutcome.Unavailable(errSecNotAvailable), keychainReadOutcome(errSecNotAvailable, null))
    }

    @Test
    fun anUnexpectedStatusIsUnavailableNotSignedOut() {
        // errSecParam and errSecMissingEntitlement: neither proves the session is gone.
        assertEquals(KeychainReadOutcome.Unavailable(-50), keychainReadOutcome(-50, null))
        assertEquals(KeychainReadOutcome.Unavailable(-34018), keychainReadOutcome(-34018, null))
    }

    @Test
    fun onlyItemNotFoundMeansNoSession() {
        assertEquals(KeychainReadOutcome.NoSession, keychainReadOutcome(errSecItemNotFound, null))
    }

    @Test
    fun aFoundItemIsItsText() {
        val outcome = assertIs<KeychainReadOutcome.Found>(keychainReadOutcome(errSecSuccess, utf8("payload")))
        assertEquals("payload", outcome.text)
    }

    @Test
    fun aFoundItemWithoutReadableDataIsCorrupt() {
        assertEquals(KeychainReadOutcome.Corrupt, keychainReadOutcome(errSecSuccess, null))
        val notUtf8 = NSString.create(string = "\u00e9").dataUsingEncoding(5u)!! // NSISOLatin1StringEncoding
        assertEquals(KeychainReadOutcome.Corrupt, keychainReadOutcome(errSecSuccess, notUtf8))
    }
}
