package com.moyi.app.data.database

import platform.Security.errSecInteractionNotAllowed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The real Keychain cannot be reached from unsigned simulator tests (-34018); the mapping is tested on its own. */
class KeychainKeyReadTest {
    @Test
    fun aStoredKeyOf32BytesIsFound() {
        val key = ByteArray(DATABASE_KEY_BYTES) { 1 }
        assertContentEquals(key, assertIs<DatabaseKeyRead.Found>(keychainKeyRead(errSecSuccess, key)).key)
    }

    @Test
    fun anItemThatIsNotAKeyIsLost() {
        assertEquals(DatabaseKeyRead.Lost, keychainKeyRead(errSecSuccess, ByteArray(3)))
        assertEquals(DatabaseKeyRead.Lost, keychainKeyRead(errSecSuccess, null))
    }

    @Test
    fun noItemIsMissing() {
        assertEquals(DatabaseKeyRead.Missing, keychainKeyRead(errSecItemNotFound, null))
    }

    @Test
    fun aLockedKeychainIsUnavailableSoTheDatabaseIsKept() {
        assertIs<DatabaseKeyRead.Unavailable>(keychainKeyRead(errSecInteractionNotAllowed, null))
        assertIs<DatabaseKeyRead.Unavailable>(keychainKeyRead(-34018, null))
    }
}
