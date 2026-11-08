package com.moyi.app.data.database

/**
 * Where the database key lives: wrapped by the Android Keystore in a no-backup file,
 * or in the iOS Keychain (`AfterFirstUnlockThisDeviceOnly`). Never in preferences,
 * never in a log. One key per install; the platform stores make it.
 *
 * Calls block (Keystore and Keychain are synchronous); [EncryptedDatabaseOpener]
 * calls them off the main thread.
 */
internal interface DatabaseKeyStore {
    fun read(): DatabaseKeyRead

    /** Makes a new random key ([DATABASE_KEY_BYTES] bytes from a CSPRNG), stores it, returns it. Replaces any old one. */
    fun create(): ByteArray

    /** Deletes the key. Must not throw when there is none. */
    fun delete()
}

/** What reading the stored database key came to. */
internal sealed interface DatabaseKeyRead {
    class Found(val key: ByteArray) : DatabaseKeyRead {
        override fun toString(): String = "Found(<redacted>)"
    }

    /** No key was ever stored on this install. */
    data object Missing : DatabaseKeyRead

    /**
     * A key was stored but is gone for good: its Keystore wrapping key was
     * invalidated or deleted, the wrapped bytes do not authenticate, or the Keychain
     * item is not a key. A database encrypted under it can never be opened again.
     */
    data object Lost : DatabaseKeyRead

    /**
     * The store could not answer right now (a Keystore hiccup; the Keychain before
     * the first unlock since boot). The key may be fine: nothing is deleted.
     */
    data class Unavailable(val reason: String) : DatabaseKeyRead
}

/** 256 bits. */
internal const val DATABASE_KEY_BYTES = 32

/**
 * The key as SQLCipher's raw-key passphrase, `x'<64 hex digits>'`. SQLCipher uses
 * these bytes as the AES-256 key directly and skips PBKDF2: the key is already 256
 * random bits, so stretching it would only slow every open (doc: SQLCipher "Raw Key
 * Data"). Never logged.
 */
internal fun ByteArray.toRawKeyPassphrase(): String {
    require(size == DATABASE_KEY_BYTES) { "A database key is $DATABASE_KEY_BYTES bytes" }
    val hex = "0123456789ABCDEF"
    val builder = StringBuilder(4 + size * 2).append("x'")
    forEach { byte ->
        val value = byte.toInt() and 0xFF
        builder.append(hex[value ushr 4]).append(hex[value and 0x0F])
    }
    return builder.append('\'').toString()
}
