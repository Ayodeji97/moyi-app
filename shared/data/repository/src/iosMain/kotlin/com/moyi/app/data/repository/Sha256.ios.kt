@file:OptIn(ExperimentalForeignApi::class)

package com.moyi.app.data.repository

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

internal actual fun sha256(bytes: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    // addressOf(0) of an empty array is out of bounds: hash a one-byte buffer with length 0 instead.
    val input = if (bytes.isEmpty()) ByteArray(1) else bytes
    input.usePinned { source ->
        digest.usePinned { out ->
            CC_SHA256(source.addressOf(0), bytes.size.convert(), out.addressOf(0))
        }
    }
    return digest.asByteArray()
}
