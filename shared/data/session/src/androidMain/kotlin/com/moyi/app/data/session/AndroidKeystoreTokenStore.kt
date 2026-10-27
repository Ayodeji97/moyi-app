package com.moyi.app.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The session in a small file encrypted with AES-256-GCM under a key that
 * lives in the Android Keystore and never leaves it (non-exportable; on most
 * devices it is held in the TEE or StrongBox).
 *
 * - The file is in `noBackupFilesDir`: Auto Backup and device-to-device
 *   transfer skip it, so a session never reappears on another phone, where
 *   the key it was encrypted with does not exist anyway.
 * - Written through [AtomicFile]: a crash mid-write leaves the old file, not
 *   half a new one.
 * - The Keystore picks the IV (randomized encryption is on by default); it is
 *   stored in front of the ciphertext. Layout: `[version][ivLength][iv][ciphertext+tag]`.
 * - Anything that fails to decrypt (a changed or invalidated key after a lock
 *   screen change, a restored file, a tampered byte: AEADBadTagException,
 *   KeyPermanentlyInvalidatedException and the rest) wipes the file and the
 *   key and reads as "no session", which signs the person out. There is no
 *   recovering a session whose key is gone.
 *
 * Not EncryptedSharedPreferences: the security-crypto library is deprecated.
 */
class AndroidKeystoreTokenStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecureTokenStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val mutex = Mutex()
    private val log = Logger.withTag("SessionStore")

    override suspend fun read(): StoredSession? = withContext(dispatcher) {
        mutex.withLock {
            if (!file.baseFile.exists()) return@withLock null
            val session = try {
                StoredSessionCodec.decode(decrypt(file.readFully()).decodeToString())
            } catch (e: Exception) {
                log.w { "Stored session unreadable (${e::class.simpleName}); wiping it" }
                null
            }
            if (session == null) wipe()
            session
        }
    }

    override suspend fun write(session: StoredSession) = withContext(dispatcher) {
        mutex.withLock {
            val bytes = encrypt(StoredSessionCodec.encode(session).encodeToByteArray())
            val out = file.startWrite()
            try {
                out.write(bytes)
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }
    }

    override suspend fun clear() = withContext(dispatcher) {
        mutex.withLock { wipe() }
    }

    private fun wipe() {
        file.delete()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain)
        return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + sealed
    }

    private fun decrypt(data: ByteArray): ByteArray {
        require(data.size > 2 && data[0] == FORMAT_VERSION) { "Unknown session file format" }
        val ivLength = data[1].toInt()
        require(ivLength in 12..16 && data.size > 2 + ivLength) { "Malformed session file" }
        val iv = data.copyOfRange(2, 2 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val existingKey = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("Session key missing")
        cipher.init(Cipher.DECRYPT_MODE, existingKey, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(data, 2 + ivLength, data.size - 2 - ivLength)
    }

    private fun key(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "moyi.session.v1"
        const val FILE_NAME = "moyi-session.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1
    }
}
