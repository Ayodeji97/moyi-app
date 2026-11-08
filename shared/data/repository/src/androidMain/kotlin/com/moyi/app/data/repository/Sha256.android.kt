package com.moyi.app.data.repository

import java.security.MessageDigest

internal actual fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
