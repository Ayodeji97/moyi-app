package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.isWebUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The guard in front of both in-app browsers: SFSafariViewController throws on any other scheme. */
class IsWebUrlTest {
    @Test
    fun httpsAndHttpWithAHostOpen() {
        assertTrue(isWebUrl("https://getmoyi.app/terms"))
        assertTrue(isWebUrl("HTTPS://example.com"))
        assertTrue(isWebUrl("http://localhost:8080/privacy"))
    }

    @Test
    fun anythingElseIsRefused() {
        listOf("javascript:alert(1)", "mailto:a@b.c", "https://", "https:///path", "example.com/terms", "", "ftp://example.com")
            .forEach { assertFalse(isWebUrl(it), "expected '$it' to be refused") }
    }
}
