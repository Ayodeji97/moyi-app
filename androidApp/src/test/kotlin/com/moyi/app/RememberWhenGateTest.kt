package com.moyi.app

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Remember when follows the build type (owner ruling 2026-11-18): offered in debug builds,
 * hidden in release builds until the backend says whether search by meaning is live. `check`
 * runs this for both build types, as ApiBaseUrlTest, so CI proves a release build hides it.
 */
class RememberWhenGateTest {
    @Test
    fun `remember when is on in debug and off in release`() {
        assertEquals(BuildConfig.DEBUG, BuildConfig.REMEMBER_WHEN, "build type ${BuildConfig.BUILD_TYPE}")
    }
}
