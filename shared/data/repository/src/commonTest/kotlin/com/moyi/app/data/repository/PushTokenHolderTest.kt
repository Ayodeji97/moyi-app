package com.moyi.app.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * [PushTokenHolder]: what the platform's FCM SDK reported, and nothing before it does (the
 * build without Firebase configured never registers a token).
 */
class PushTokenHolderTest {
    @Test
    fun withoutFirebaseThereIsNoToken() = runTest {
        assertNull(PushTokenHolder().token.first())
    }

    @Test
    fun theLatestTokenTheProviderReportedIsTheToken() = runTest {
        val holder = PushTokenHolder()
        holder.update("fcm-1")
        holder.update("fcm-2")
        assertEquals("fcm-2", holder.token.first())
    }

    @Test
    fun aBlankTokenIsNoToken() = runTest {
        val holder = PushTokenHolder()
        holder.update("fcm-1")
        holder.update("  ")
        assertNull(holder.token.first())
    }

    @Test
    fun theTokenNeverReachesALogLine() {
        val holder = PushTokenHolder()
        holder.update("fcm-secret")
        assertFalse("fcm-secret" in holder.toString())
    }
}
