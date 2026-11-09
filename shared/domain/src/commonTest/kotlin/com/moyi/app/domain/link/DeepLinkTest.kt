package com.moyi.app.domain.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DeepLinkTest {
    // The shape SecureRandomTokenGenerator makes: 32 bytes, base64url, no padding (43 characters).
    private val token = "q3Zx-8_kP0aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456"

    @Test
    fun `the three links the backend builds`() {
        assertEquals(DeepLink.VerifyEmail(token), DeepLink.parse("https://getmoyi.app/verify?token=$token"))
        assertEquals(DeepLink.ResetPassword(token), DeepLink.parse("https://getmoyi.app/reset-password?token=$token"))
        assertEquals(DeepLink.Invite("7KQ4MZ"), DeepLink.parse("https://getmoyi.app/i/7KQ4MZ"))
    }

    @Test
    fun `scheme and host are compared without case and the path with it`() {
        assertEquals(DeepLink.VerifyEmail(token), DeepLink.parse("HTTPS://GetMoyi.App/verify?token=$token"))
        assertEquals(DeepLink.Unknown, DeepLink.parse("https://getmoyi.app/Verify?token=$token"))
    }

    @Test
    fun `an invite code is upper-cased and its shape left to the join screen`() {
        assertEquals(DeepLink.Invite("7KQ4MZ"), DeepLink.parse("https://getmoyi.app/i/7kq4mz"))
        // 0 and O are outside the alphabet: the join screen says so, as for a typed code.
        assertEquals(DeepLink.Invite("0O1"), DeepLink.parse("https://getmoyi.app/i/0o1"))
    }

    @Test
    fun `a trailing slash and other parameters and a fragment change nothing`() {
        assertEquals(DeepLink.VerifyEmail(token), DeepLink.parse("https://getmoyi.app/verify/?token=$token"))
        assertEquals(DeepLink.VerifyEmail(token), DeepLink.parse("https://getmoyi.app/verify?utm=mail&token=$token&x=1#top"))
        assertEquals(DeepLink.Invite("7KQ4MZ"), DeepLink.parse("https://getmoyi.app/i/7KQ4MZ/"))
        assertEquals(DeepLink.Invite("7KQ4MZ"), DeepLink.parse("https://getmoyi.app/i/7KQ4MZ?ref=share#x"))
        assertEquals(DeepLink.Invite("7KQ4MZ"), DeepLink.parse("  https://getmoyi.app/i/7KQ4MZ\n"))
    }

    @Test
    fun `any other host is refused`() {
        listOf(
            "https://www.getmoyi.app/verify?token=$token",
            "https://api.getmoyi.app/verify?token=$token",
            "https://getmoyi.app.evil.example/verify?token=$token",
            "https://evilgetmoyi.app/verify?token=$token",
            "https://evil.example/verify?token=$token&next=https://getmoyi.app/verify",
            "https://getmoyi.app@evil.example/verify?token=$token",
            "https://evil.example@getmoyi.app/verify?token=$token",
            "https://getmoyi.app:8443/verify?token=$token",
            "http://getmoyi.app/verify?token=$token",
            "moyi://getmoyi.app/verify?token=$token",
            "getmoyi.app/verify?token=$token",
        ).forEach { assertEquals(DeepLink.Unknown, DeepLink.parse(it), it) }
    }

    @Test
    fun `a path the app does not route is unknown`() {
        listOf(
            "https://getmoyi.app",
            "https://getmoyi.app/",
            "https://getmoyi.app/privacy",
            "https://getmoyi.app/verify-email?token=$token",
            "https://getmoyi.app/i",
            "https://getmoyi.app/i/",
            "https://getmoyi.app/i/7KQ4MZ/extra",
            "https://getmoyi.app/x/i/7KQ4MZ",
        ).forEach { assertEquals(DeepLink.Unknown, DeepLink.parse(it), it) }
    }

    @Test
    fun `a missing or empty or malformed token is unknown and not sent on`() {
        listOf(
            "https://getmoyi.app/verify",
            "https://getmoyi.app/verify?token=",
            "https://getmoyi.app/verify?mytoken=$token",
            "https://getmoyi.app/reset-password?token=abc%24(rm)",
            "https://getmoyi.app/reset-password?token=abc.def",
            "https://getmoyi.app/verify?token=" + "a".repeat(DeepLink.MAX_TOKEN_LENGTH + 1),
            "https://getmoyi.app/i/7KQ%204MZ",
            "https://getmoyi.app/i/" + "A".repeat(DeepLink.MAX_CODE_LENGTH + 1),
        ).forEach { assertEquals(DeepLink.Unknown, DeepLink.parse(it), it) }
    }

    @Test
    fun `a link never prints its token`() {
        assertFalse(token in DeepLink.VerifyEmail(token).toString())
        assertFalse(token in DeepLink.ResetPassword(token).toString())
    }
}
