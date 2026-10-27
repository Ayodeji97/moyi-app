@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.moyi.app.data.session

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
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
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecInteractionNotAllowed
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
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus

/**
 * The session as one generic-password item in the iOS Keychain.
 *
 * - `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: readable after the
 *   first unlock since boot (so a background refresh works with the phone
 *   locked), and never in a backup or on another device.
 * - The Keychain outlives the app: deleting Moyi does not delete the item.
 *   A reinstall must start signed out, so the first operation after install
 *   (no `moyi.installed` flag in UserDefaults, which IS deleted with the app)
 *   clears the item, then sets the flag.
 * - Written with update-then-add, never delete-then-add: a crash between a
 *   delete and an add would lose a session the server has already rotated.
 *
 * Core Foundation objects are created with a +1 retain count and released
 * in `finally`; `CFBridgingRetain` hands a Kotlin/ObjC object to CF (+1) and
 * `CFBridgingRelease` takes a CF result back under ARC.
 */
class KeychainTokenStore(
    private val service: String = SERVICE,
    private val account: String = ACCOUNT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecureTokenStore {
    private val mutex = Mutex()
    private var installChecked = false
    private val log = Logger.withTag("SessionStore")

    override suspend fun read(): StoredSession? = locked {
        // A locked Keychain (before the first unlock after a reboot) throws
        // TokenStoreUnavailableException out of copyItem, so the caller keeps
        // the session and retries. Any other read failure reads as no session
        // but keeps the item.
        val text = try {
            copyItem()
        } catch (e: IllegalStateException) {
            log.w { e.message.orEmpty() }
            null
        } ?: return@locked null
        val session = StoredSessionCodec.decode(text)
        if (session == null) {
            log.w { "Stored session unreadable; wiping it" }
            deleteItem()
        }
        session
    }

    override suspend fun write(session: StoredSession) = locked {
        val data = NSString.create(string = StoredSessionCodec.encode(session)).dataUsingEncoding(NSUTF8StringEncoding)
            ?: error("Could not encode the session")
        val dataRef = CFBridgingRetain(data)
        try {
            val updateStatus = withQuery { query ->
                withDictionary(kSecValueData to dataRef) { attributes -> SecItemUpdate(query, attributes) }
            }
            if (updateStatus == errSecItemNotFound) {
                val addStatus = withQuery(kSecValueData to dataRef, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly) {
                    SecItemAdd(it, null)
                }
                check(addStatus == errSecSuccess) { "Keychain add failed: $addStatus" }
            } else {
                check(updateStatus == errSecSuccess) { "Keychain update failed: $updateStatus" }
            }
        } finally {
            CFRelease(dataRef)
        }
    }

    override suspend fun clear() = locked { deleteItem() }

    private suspend fun <T> locked(block: () -> T): T = withContext(dispatcher) {
        mutex.withLock {
            if (!installChecked) {
                // A Keychain that refuses the wipe now is retried on the next
                // call; the flag is set only after a successful wipe.
                installChecked = try {
                    clearIfFreshInstall()
                    true
                } catch (e: IllegalStateException) {
                    log.w { e.message.orEmpty() }
                    false
                }
            }
            block()
        }
    }

    private fun clearIfFreshInstall() {
        val defaults = NSUserDefaults.standardUserDefaults
        if (!defaults.boolForKey(INSTALLED_FLAG)) {
            deleteItem()
            defaults.setBool(true, forKey = INSTALLED_FLAG)
        }
    }

    private fun copyItem(): String? = withQuery(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne) { query ->
        memScoped {
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> {
                    val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
                    NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                }
                errSecItemNotFound -> null
                else -> throw keychainReadFailure(status)
            }
        }
    }

    private fun deleteItem() {
        val status: OSStatus = withQuery { SecItemDelete(it) }
        check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain delete failed: $status" }
    }

    /** The item's identity (class, service, account) plus [extra], released after [block]. */
    private fun <T> withQuery(vararg extra: Pair<CFTypeRef?, CFTypeRef?>, block: (CFDictionaryRef?) -> T): T {
        val serviceRef = CFBridgingRetain(NSString.create(string = service))
        val accountRef = CFBridgingRetain(NSString.create(string = account))
        try {
            return withDictionary(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
                kSecAttrAccount to accountRef,
                *extra,
                block = block,
            )
        } finally {
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }

    private fun <T> withDictionary(vararg pairs: Pair<CFTypeRef?, CFTypeRef?>, block: (CFDictionaryRef?) -> T): T {
        val dictionary: CFMutableDictionaryRef? = CFDictionaryCreateMutable(
            null,
            pairs.size.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        try {
            pairs.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
            return block(dictionary)
        } finally {
            CFRelease(dictionary)
        }
    }

    companion object {
        const val SERVICE = "com.moyi.app.session"
        const val ACCOUNT = "session"
        const val INSTALLED_FLAG = "moyi.installed"
    }
}

/**
 * What a failed `SecItemCopyMatching` means. `errSecInteractionNotAllowed`
 * (-25308) is a locked Keychain (before the first unlock after a reboot, for
 * an `AfterFirstUnlock` item): the session is intact, just not readable yet,
 * so it is [TokenStoreUnavailableException], never "signed out".
 */
internal fun keychainReadFailure(status: OSStatus): Exception =
    if (status == errSecInteractionNotAllowed) {
        TokenStoreUnavailableException("Keychain locked: $status")
    } else {
        IllegalStateException("Keychain read failed: $status")
    }
