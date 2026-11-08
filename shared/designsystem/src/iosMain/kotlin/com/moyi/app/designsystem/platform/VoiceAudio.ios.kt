@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.moyi.app.designsystem.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/** A copy of [this] NSData's bytes, for Kotlin. */
internal fun NSData.copyBytes(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

/** A copy of [this] as NSData, for Foundation. */
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

/**
 * An Objective-C initialiser that may return nil (a file AVFoundation cannot open, a
 * recorder it cannot make). Kotlin types the constructor as never null, so a nil is caught
 * here, as an exception, and read as null.
 */
internal inline fun <T : Any> failable(make: () -> T?): T? = try {
    make()
} catch (_: Throwable) {
    null
}
