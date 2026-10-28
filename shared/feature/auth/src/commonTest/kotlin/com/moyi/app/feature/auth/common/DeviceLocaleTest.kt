package com.moyi.app.feature.auth.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceLocaleTest {
    private val backendPattern = Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$")

    @Test
    fun tagsAreFittedToTheBackendPattern() {
        val cases = mapOf(
            "en" to "en",
            "en-GB" to "en-GB",
            "pt-PT" to "pt-PT",
            "zh-Hans-CN" to "zh-CN",
            "sr-Latn" to "sr-Latn",
            "es-419" to "es-419",
            "en_US" to "en-US",
            "" to "en",
            "und" to "en",
            "C" to "en",
        )
        for ((input, expected) in cases) {
            val out = backendLanguageTag(input)
            assertEquals(expected, out, "for '$input'")
            assertTrue(backendPattern.matches(out))
        }
    }
}
