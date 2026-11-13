package com.moyi.app

import com.moyi.app.domain.link.DeepLink
import com.moyi.app.feature.auth.navigation.LoginRoute
import com.moyi.app.feature.auth.navigation.ResetLinkSentRoute
import com.moyi.app.feature.auth.navigation.ResetPasswordRoute
import com.moyi.app.feature.auth.navigation.VerifyRoute
import com.moyi.app.feature.onboarding.navigation.JoinCodeRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkRoutingTest {
    private val token = "q3Zx-8_kP0aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456"
    private val verify = DeepLink.VerifyEmail(token)
    private val reset = DeepLink.ResetPassword(token)
    private val invite = DeepLink.Invite("7KQ4MZ")
    private val me = "amara@example.com"

    @Test
    fun `nothing opens while the shell is settling`() {
        assertEquals(LinkRouting.Wait, accountLinkRouting(verify, LinkPlace.Settling, null, null))
        assertEquals(LinkRouting.Wait, accountLinkRouting(reset, LinkPlace.Settling, null, me))
        assertEquals(LinkRouting.Wait, inviteRouting(invite, LinkPlace.Settling))
    }

    @Test
    fun `signed out and a verify link opens verify over what is showing and with no address`() {
        assertEquals(
            LinkRouting.Push(VerifyRoute(email = "", linkToken = token, signedIn = false)),
            accountLinkRouting(verify, LinkPlace.SignedOut, null, null),
        )
        assertEquals(
            LinkRouting.Push(VerifyRoute(email = "", linkToken = token, signedIn = false)),
            accountLinkRouting(verify, LinkPlace.SignedOut, LoginRoute(), null),
        )
    }

    @Test
    fun `on verify after signing up and the link takes its place and keeps the address`() {
        assertEquals(
            LinkRouting.Push(VerifyRoute(email = me, linkToken = token), replaceCurrent = true),
            accountLinkRouting(verify, LinkPlace.SignedOut, VerifyRoute(me), null),
        )
    }

    @Test
    fun `signed in and not yet verified and a verify link carries the session's address and returns to the signed-in start`() {
        listOf(LinkPlace.Gate, LinkPlace.Elsewhere).forEach {
            assertEquals(
                LinkRouting.Push(VerifyRoute(email = me, linkToken = token, signedIn = true)),
                accountLinkRouting(verify, it, null, me),
                it.toString(),
            )
        }
    }

    @Test
    fun `signed in and already verified and a verify link opens nothing and says the email is already confirmed`() {
        // Only a verified person reaches these, whatever the session's sign-in-time flag says.
        listOf(LinkPlace.Onboarding, LinkPlace.SignedInApp, LinkPlace.Archived).forEach {
            assertEquals(LinkRouting.AlreadyVerified, accountLinkRouting(verify, it, null, me, signedInVerified = false), it.toString())
        }
        // Anywhere else (Account, say) the flag decides: the session's, or a verified-only screen under it.
        assertEquals(LinkRouting.AlreadyVerified, accountLinkRouting(verify, LinkPlace.Elsewhere, null, me, signedInVerified = true))
        assertEquals(LinkRouting.AlreadyVerified, accountLinkRouting(verify, LinkPlace.Gate, null, me, signedInVerified = true))
    }

    @Test
    fun `signed out the verified flag changes nothing and a reset link is never turned away`() {
        assertEquals(
            LinkRouting.Push(VerifyRoute(email = "", linkToken = token, signedIn = false)),
            accountLinkRouting(verify, LinkPlace.SignedOut, null, null, signedInVerified = true),
        )
        assertEquals(
            LinkRouting.Push(ResetPasswordRoute(email = me, linkToken = token)),
            accountLinkRouting(reset, LinkPlace.SignedInApp, null, me, signedInVerified = true),
        )
    }

    @Test
    fun `a reset link continues the reset in progress with its address`() {
        assertEquals(
            LinkRouting.Push(ResetPasswordRoute(email = me, linkToken = token)),
            accountLinkRouting(reset, LinkPlace.SignedOut, ResetLinkSentRoute(me), null),
        )
        assertEquals(
            LinkRouting.Push(ResetPasswordRoute(email = me, linkToken = token), replaceCurrent = true),
            accountLinkRouting(reset, LinkPlace.SignedOut, ResetPasswordRoute(me), null),
        )
    }

    @Test
    fun `a reset link from nowhere opens choose a new password and with the address only if signed in`() {
        assertEquals(
            LinkRouting.Push(ResetPasswordRoute(email = "", linkToken = token)),
            accountLinkRouting(reset, LinkPlace.SignedOut, null, null),
        )
        assertEquals(
            LinkRouting.Push(ResetPasswordRoute(email = me, linkToken = token)),
            accountLinkRouting(reset, LinkPlace.SignedInApp, null, me),
        )
    }

    @Test
    fun `an invite waits for a verified session and for the bond the person just made`() {
        listOf(LinkPlace.Settling, LinkPlace.SignedOut, LinkPlace.Gate, LinkPlace.Elsewhere).forEach {
            assertEquals(LinkRouting.Wait, inviteRouting(invite, it), it.toString())
        }
    }

    @Test
    fun `an invite opens the prefilled code screen and a root in onboarding and over the archive or Today`() {
        val route = JoinCodeRoute(prefilledCode = "7KQ4MZ")
        assertEquals(LinkRouting.Reset(route), inviteRouting(invite, LinkPlace.Onboarding))
        assertEquals(LinkRouting.Push(route), inviteRouting(invite, LinkPlace.Archived))
        // Over Today the person may be in a bond nobody has joined: the code screen looks for it.
        assertEquals(LinkRouting.AboveToday(JoinCodeRoute.fromLinkOverToday("7KQ4MZ")), inviteRouting(invite, LinkPlace.SignedInApp))
    }

    @Test
    fun `only an invite over Today looks for the person's own empty bond`() {
        val overToday = (inviteRouting(invite, LinkPlace.SignedInApp) as LinkRouting.AboveToday).route as JoinCodeRoute
        assertEquals(true, overToday.findEmptyBond)
        assertEquals("7KQ4MZ", overToday.prefilledCode)
        // Nothing is ended on a guess: the route carries no bond, only the look for one.
        assertEquals(null, overToday.leaving)
        val inOnboarding = (inviteRouting(invite, LinkPlace.Onboarding) as LinkRouting.Reset).route as JoinCodeRoute
        val overArchive = (inviteRouting(invite, LinkPlace.Archived) as LinkRouting.Push).route as JoinCodeRoute
        assertEquals(false, inOnboarding.findEmptyBond)
        assertEquals(false, overArchive.findEmptyBond)
    }

    @Test
    fun `the inbox keeps an invite apart from an account link and ignores other links`() {
        val inbox = LinkInbox()
        inbox.opened("https://getmoyi.app/i/7kq4mz")
        inbox.opened("https://getmoyi.app/verify?token=$token")
        assertEquals(invite, inbox.inviteLink.value)
        assertEquals(verify, inbox.accountLink.value)

        inbox.opened("https://getmoyi.app/privacy")
        inbox.opened("https://evil.example/i/AAAAAA")
        assertEquals(invite, inbox.inviteLink.value)
        assertEquals(verify, inbox.accountLink.value)

        // The later account link wins its slot.
        inbox.opened("https://getmoyi.app/reset-password?token=$token")
        assertEquals(reset, inbox.accountLink.value)

        inbox.consume(reset)
        inbox.consume(invite)
        assertNull(inbox.accountLink.value)
        assertNull(inbox.inviteLink.value)
    }

    @Test
    fun `consuming a link keeps a newer one`() {
        val inbox = LinkInbox()
        inbox.opened("https://getmoyi.app/verify?token=$token")
        inbox.opened("https://getmoyi.app/reset-password?token=$token")
        inbox.consume(verify)
        assertEquals(reset, inbox.accountLink.value)
    }
}
