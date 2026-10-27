package com.moyi.app.data.session

import platform.Security.errSecInteractionNotAllowed
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * The real Keychain cannot be exercised here (unsigned simulator tests get
 * -34018), so the status mapping is tested on its own.
 */
class KeychainReadFailureTest {
    @Test
    fun aLockedKeychainIsUnavailableNotSignedOut() {
        assertIs<TokenStoreUnavailableException>(keychainReadFailure(errSecInteractionNotAllowed))
    }

    @Test
    fun anyOtherFailureStaysAnOrdinaryReadFailure() {
        assertIs<IllegalStateException>(keychainReadFailure(-25300))
    }
}
