package com.moyi.app.data.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database key, wrapped: 32 random bytes from [SecureRandom], sealed with
 * AES-256-GCM under a wrapping key that lives in the Android Keystore and never
 * leaves it, in a small file in `noBackupFilesDir`. The same approach as the
 * session's `AndroidKeystoreTokenStore`; the wrapping key has its own alias.
 *
 * Why wrap instead of using a Keystore key directly: SQLCipher needs the key's
 * bytes, and a Keystore key is non-exportable by design. So the database key is
 * an ordinary random key, and the Keystore protects it at rest.
 *
 * The wrapping key needs no user authentication and is not bound to an unlocked
 * screen, so WorkManager can open the database to send the outbox while the phone
 * is locked (after the first unlock, when the app's credential-encrypted storage
 * is available at all).
 *
 * File layout: `[version][ivLength][iv][ciphertext+tag]`. Written through
 * [AtomicFile], so a crash mid-write leaves the old file or none.
 */
internal class AndroidKeystoreDatabaseKeyStore(
    context: Context,
    private val alias: String = KEY_ALIAS,
    fileName: String = FILE_NAME,
) : DatabaseKeyStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))

    override fun read(): DatabaseKeyRead {
        if (!file.baseFile.exists()) return DatabaseKeyRead.Missing
        return try {
            val key = unwrap(file.readFully())
            if (key.size == DATABASE_KEY_BYTES) DatabaseKeyRead.Found(key) else DatabaseKeyRead.Lost
        } catch (e: Exception) {
            if (isLost(e)) DatabaseKeyRead.Lost else DatabaseKeyRead.Unavailable(e::class.simpleName ?: "Exception")
        }
    }

    override fun create(): ByteArray {
        val key = ByteArray(DATABASE_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        runCatching { keyStore().deleteEntry(alias) } // A new database key gets a new wrapping key.
        val sealed = wrap(key)
        val out = file.startWrite()
        try {
            out.write(sealed)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
        return key
    }

    override fun delete() {
        file.delete()
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        return byteArrayOf(FORMAT_VERSION, iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun unwrap(data: ByteArray): ByteArray {
        require(data.size > 2 && data[0] == FORMAT_VERSION) { "Unknown key file format" }
        val ivLength = data[1].toInt()
        require(ivLength in 12..16 && data.size > 2 + ivLength) { "Malformed key file" }
        val existing = keyStore().getKey(alias, null) as? SecretKey ?: throw WrappingKeyMissingException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(TAG_BITS, data, 2, ivLength))
        return cipher.doFinal(data, 2 + ivLength, data.size - 2 - ivLength)
    }

    private fun wrappingKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private class WrappingKeyMissingException : Exception("Wrapping key missing")

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "moyi.database.v1"
        const val FILE_NAME = "moyi-database-key.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val FORMAT_VERSION: Byte = 1

        /**
         * Gone for good (the same split as the session store's `shouldWipe`): the
         * wrapping key is missing, invalidated or unrecoverable, the bytes do not
         * authenticate, or the file is not in this format. Anything else
         * (ProviderException, KeyStoreException, IOException) may pass, and is
         * [DatabaseKeyRead.Unavailable].
         */
        fun isLost(e: Exception): Boolean = e is WrappingKeyMissingException ||
            e is AEADBadTagException ||
            e is KeyPermanentlyInvalidatedException ||
            e is UnrecoverableKeyException ||
            e is IllegalArgumentException
    }
}
