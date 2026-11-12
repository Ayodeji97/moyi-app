package com.moyi.app

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The URL each build type talks to (CLAUDE.md, Configuration). `check` runs this for both
 * build types (testDebugUnitTest, testReleaseUnitTest), so CI sees both generated values:
 * a debug build never carries a production host, and a release build carries exactly
 * production, over HTTPS.
 */
class ApiBaseUrlTest {
    private val url = BuildConfig.API_BASE_URL
    private val host = URI(url).host.orEmpty().lowercase().trimEnd('.')

    @Test
    fun `debug talks to a local backend and never to production`() {
        if (!BuildConfig.DEBUG) return
        assertFalse(host == "getmoyi.app" || host.endsWith(".getmoyi.app"), "debug points at production: $url")
        assertFalse(host == "91.98.71.136", "debug points at production's address: $url")
        assertTrue(url.trimEnd('/').endsWith("/api/v1"), url)
    }

    @Test
    fun `release talks to production over https and nothing else`() {
        if (BuildConfig.DEBUG) return
        assertEquals("https://api.getmoyi.app/api/v1", url)
    }

    @Test
    fun `every build type knows its URL`() {
        assertTrue(BuildConfig.BUILD_TYPE in setOf("debug", "release"), BuildConfig.BUILD_TYPE)
        assertEquals(BuildConfig.BUILD_TYPE == "debug", BuildConfig.DEBUG)
    }
}
