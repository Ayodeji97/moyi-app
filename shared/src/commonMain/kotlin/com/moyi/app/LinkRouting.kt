package com.moyi.app

import com.moyi.app.domain.link.DeepLink
import com.moyi.app.feature.auth.navigation.ResetLinkSentRoute
import com.moyi.app.feature.auth.navigation.ResetPasswordRoute
import com.moyi.app.feature.auth.navigation.VerifyRoute
import com.moyi.app.feature.onboarding.navigation.JoinCodeRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The links that opened the app and have yet to be routed. Each platform's entry hands one
 * over ([opened]): Android's `MainActivity` from the `VIEW` intent (an App Link), iOS's
 * `onContinueUserActivity` from the universal link. The shell routes each when the app is
 * somewhere it can land ([accountLinkRouting], [inviteRouting]), then [consume]s it.
 *
 * Two slots, not one: an invite link waits for a session (sign up, verify, sign in), and
 * the verify link that comes during that wait must not push the invite out. Within a slot
 * the later link wins, as for push taps.
 *
 * Held in memory only. A link carries a credential, so it is not written anywhere; an
 * invite that was waiting when the process died is opened again from the message.
 */
class LinkInbox {
    private val account = MutableStateFlow<DeepLink?>(null)
    private val invite = MutableStateFlow<DeepLink.Invite?>(null)

    /** A [DeepLink.VerifyEmail] or [DeepLink.ResetPassword]; they need no session. */
    val accountLink: StateFlow<DeepLink?> = account.asStateFlow()

    /** An invite link: it opens once someone is signed in and verified. */
    val inviteLink: StateFlow<DeepLink.Invite?> = invite.asStateFlow()

    /**
     * [url] opened the app. Never logged (a token is a credential). A link the app does not
     * route ([DeepLink.Unknown]) changes nothing: the app simply comes to the front.
     */
    fun opened(url: String) {
        when (val link = DeepLink.parse(url)) {
            is DeepLink.VerifyEmail, is DeepLink.ResetPassword -> account.value = link
            is DeepLink.Invite -> invite.value = link
            DeepLink.Unknown -> Unit
        }
    }

    /** [link] has been routed. A newer link that arrived meanwhile is kept. */
    fun consume(link: DeepLink) {
        when (link) {
            is DeepLink.Invite -> invite.compareAndSet(link, null)
            else -> account.compareAndSet(link, null)
        }
    }
}

/** Where the shell is, as far as a waiting link cares. */
internal enum class LinkPlace {
    /** The session is being restored, the signed-in start is deciding, or Today is loading its day. */
    Settling,

    /** Nobody is signed in: the promise, sign up, verify, sign in or the reset screens. */
    SignedOut,

    /** Signed in, not verified, in no bond: the unverified gate. */
    Gate,

    /** Signed in, in no bond: onboarding (relationship type, create or join). */
    Onboarding,

    /** Signed in, the newest bond has ended: the archived bond. */
    Archived,

    /** Today is on the back stack and its day has loaded. */
    SignedInApp,

    /** Signed in, on a screen none of the above: e.g. a verify screen a link opened over the gate. */
    Elsewhere,
}

/** What the shell does with a waiting link. */
internal sealed interface LinkRouting {
    data object Wait : LinkRouting

    /** [route] over the current screen; [replaceCurrent] takes the current screen's place. */
    data class Push(val route: Any, val replaceCurrent: Boolean = false) : LinkRouting

    /** [route] as the only screen. */
    data class Reset(val route: Any) : LinkRouting

    /** Back to Today, then [route] over it. */
    data class AboveToday(val route: Any) : LinkRouting
}

/**
 * A verify or reset link. Neither needs a session (the token is the proof), so it opens as
 * soon as the shell has a screen, signed in or out.
 *
 * - [current] is the route on screen when it is one of the screens the link continues: on
 *   Verify (just signed up) the link takes its place, keeping the address; on Reset link
 *   sent it pushes Choose a new password with that address, as the screen's own link does;
 *   on Choose a new password it takes its place. The address is what signs the person in
 *   afterwards.
 * - Anywhere else the screen is pushed over what is showing, so back returns there. The
 *   address is [signedInEmail] when someone is signed in, and empty when not: the phone
 *   does not know which account the email went to, and the screens say what to do then.
 * - Signed in, a confirmed address returns to the signed-in start ([VerifyRoute.signedIn]),
 *   which reads `GET /me` again and moves on from the gate. If the link is for another
 *   account, that account is confirmed (the token is the proof) and this one is unchanged.
 */
internal fun accountLinkRouting(link: DeepLink, place: LinkPlace, current: Any?, signedInEmail: String?): LinkRouting {
    if (place == LinkPlace.Settling) return LinkRouting.Wait
    val signedIn = place != LinkPlace.SignedOut
    val email = if (signedIn) signedInEmail.orEmpty() else ""
    return when (link) {
        is DeepLink.VerifyEmail -> when (current) {
            is VerifyRoute -> LinkRouting.Push(current.copy(linkToken = link.token), replaceCurrent = true)
            else -> LinkRouting.Push(VerifyRoute(email = email, linkToken = link.token, signedIn = signedIn))
        }
        is DeepLink.ResetPassword -> when (current) {
            is ResetPasswordRoute -> LinkRouting.Push(current.copy(linkToken = link.token), replaceCurrent = true)
            is ResetLinkSentRoute -> LinkRouting.Push(ResetPasswordRoute(email = current.email, linkToken = link.token))
            else -> LinkRouting.Push(ResetPasswordRoute(email = email, linkToken = link.token))
        }
        is DeepLink.Invite, DeepLink.Unknown -> LinkRouting.Wait
    }
}

/**
 * An invite link. `GET /invites/{code}` needs a session and a verified address (states.md
 * §2: open the link, sign up or in, verify, then the code resolves), so it waits until both.
 * Then it opens "Join · code from link", the prefilled code screen, whose Continue resolves
 * the code and shows the confirm step (inviter and bond name) before anything is accepted.
 *
 * - Onboarding (no bond): the code screen replaces it, a root with no back (states.md §9).
 * - The archived bond: over it, so back returns there.
 * - Today: over Today. Joining while in an open bond is the server's to refuse
 *   (`BOND_LIMIT_REACHED`), and the confirm screen already says so.
 *
 * Whoever is signed in is the one who joins: the confirm step names the inviter and the
 * bond first, which is what makes a link opened on the wrong account safe to back out of.
 */
internal fun inviteRouting(link: DeepLink.Invite, place: LinkPlace): LinkRouting {
    val route = JoinCodeRoute(prefilledCode = link.code)
    return when (place) {
        LinkPlace.Settling, LinkPlace.SignedOut, LinkPlace.Gate, LinkPlace.Elsewhere -> LinkRouting.Wait
        LinkPlace.Onboarding -> LinkRouting.Reset(route)
        LinkPlace.Archived -> LinkRouting.Push(route)
        LinkPlace.SignedInApp -> LinkRouting.AboveToday(route)
    }
}
