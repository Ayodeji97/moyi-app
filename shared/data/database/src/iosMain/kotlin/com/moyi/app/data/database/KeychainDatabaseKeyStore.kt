@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.moyi.app.data.database

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus

/**
 * The database key as one generic-password item in the iOS Keychain: 32 bytes from
 * `SecRandomCopyBytes` (the system CSPRNG).
 *
 * - `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: readable after the first
 *   unlock since boot, so the background outbox sender (BGTaskScheduler) can open
 *   the database with the phone locked; never in a backup, never on another device.
 *   Before the first unlock the read is `errSecInteractionNotAllowed`, which is
 *   [DatabaseKeyRead.Unavailable]: the file is kept and that run uses memory.
 * - Unlike the session item, a key left behind by a deleted app needs no install
 *   check: the database file went with the app, and [EncryptedDatabaseOpener] makes
 *   a new key for every new file, replacing the old item.
 */
internal class KeychainDatabaseKeyStore(
    private val service: String = SERVICE,
    private val account: String = ACCOUNT,
) : DatabaseKeyStore {
    override fun read(): DatabaseKeyRead = withQuery(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { query ->
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            val data = result.value?.let { CFBridgingRelease(it) } as? NSData
            keychainKeyRead(status, data?.toByteArray())
        }
    }

    override fun create(): ByteArray {
        val key = ByteArray(DATABASE_KEY_BYTES)
        val random = key.usePinned { SecRandomCopyBytes(kSecRandomDefault, key.size.convert(), it.addressOf(0)) }
        check(random == errSecSuccess) { "SecRandomCopyBytes failed: $random" }
        delete()
        val data = key.usePinned { NSData.create(bytes = it.addressOf(0), length = key.size.convert()) }
        val dataRef = CFBridgingRetain(data)
        try {
            val status = withQuery(kSecValueData to dataRef, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) {
                SecItemAdd(it, null)
            }
            check(status == errSecSuccess) { "Keychain add failed: $status" }
        } finally {
            CFRelease(dataRef)
        }
        return key
    }

    override fun delete() {
        val status: OSStatus = withQuery { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain delete failed: $status" }
    }

    private fun <T> withQuery(vararg extra: Pair<CFTypeRef?, CFTypeRef?>, block: (CFDictionaryRef?) -> T): T {
        val serviceRef = CFBridgingRetain(NSString.create(string = service))
        val accountRef = CFBridgingRetain(NSString.create(string = account))
        val pairs = arrayOf(kSecClass to kSecClassGenericPassword, kSecAttrService to serviceRef, kSecAttrAccount to accountRef, *extra)
        val dictionary = CFDictionaryCreateMutable(null, pairs.size.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            pairs.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
            return block(dictionary)
        } finally {
            CFRelease(dictionary)
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }

    companion object {
        const val SERVICE = "com.moyi.app.database"
        const val ACCOUNT = "key"
    }
}

/**
 * Maps a `SecItemCopyMatching` status and its bytes to a key read. Only
 * `errSecItemNotFound` means there is no key; an item that is not 32 bytes is
 * [DatabaseKeyRead.Lost]; any other status (`errSecInteractionNotAllowed` before the
 * first unlock, `errSecNotAvailable`, anything unforeseen) is
 * [DatabaseKeyRead.Unavailable], so a Keychain hiccup never deletes the database.
 */
internal fun keychainKeyRead(status: OSStatus, bytes: ByteArray?): DatabaseKeyRead = when (status) {
    errSecSuccess -> if (bytes?.size == DATABASE_KEY_BYTES) DatabaseKeyRead.Found(bytes) else DatabaseKeyRead.Lost
    errSecItemNotFound -> DatabaseKeyRead.Missing
    else -> DatabaseKeyRead.Unavailable("OSStatus $status")
}

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    return if (size == 0) ByteArray(0) else bytes!!.readBytes(size)
}
