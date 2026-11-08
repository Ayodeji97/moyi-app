package com.moyi.app

import com.moyi.app.domain.notification.PushPayload
import com.moyi.app.domain.notification.PushTarget
import com.moyi.app.feature.bond.navigation.BondSettingsGraph
import com.moyi.app.feature.today.navigation.ArchiveRoute
import com.moyi.app.feature.today.navigation.StreakRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one tapped push the app has yet to open. Each platform's entry hands a tap over
 * ([tapped]): Android's `MainActivity` from the notification's intent, iOS's
 * `UNUserNotificationCenterDelegate` from `userInfo`. The shell routes it when the
 * signed-in app is showing, then [consume]s it.
 *
 * One slot, not a queue: a second tap before the first is routed means the person changed
 * their mind, and the later one wins.
 */
class PushTapInbox {
    private val pending = MutableStateFlow<PushTarget?>(null)

    val target: StateFlow<PushTarget?> = pending.asStateFlow()

    /** [data]: the push's two keys, as the platform found them. Not a push: nothing changes. */
    fun tapped(data: Map<String, String?>) {
        PushPayload.targetOf(data)?.let { pending.value = it }
    }

    /** [target] has been routed or dropped. A newer tap that arrived meanwhile is kept. */
    fun consume(target: PushTarget) {
        pending.compareAndSet(target, null)
    }
}

/** Where the shell is, as far as a pending tap cares. */
internal enum class ShellPlace {
    /**
     * Not settled yet: the session is being restored, the signed-in start is asking the
     * server where this person is, Today is loading its day, or nobody is signed in (the
     * promise, sign in, sign up).
     * A tap waits here: once someone signs in and lands in the app, it opens.
     */
    Settling,

    /**
     * Today is on the back stack and its day has loaded: the signed-in app, where every
     * target lives. The Archive and the Streak take the bond from that day.
     */
    SignedInApp,

    /**
     * Signed in, but the signed-in start sent this person somewhere with no Today: onboarding
     * (no bond), the unverified gate, or the archived bond. No target has a screen there.
     */
    OutsideTheApp,
}

/** What the shell does with a pending tap at [place]. */
internal sealed interface PushRouting {
    data object Wait : PushRouting

    data object Drop : PushRouting

    /** Back to Today, then push [routes] over it, in order. */
    data class Open(val routes: List<Any>) : PushRouting
}

internal fun pushRouting(target: PushTarget, place: ShellPlace): PushRouting = when (place) {
    ShellPlace.Settling -> PushRouting.Wait
    ShellPlace.OutsideTheApp -> PushRouting.Drop
    ShellPlace.SignedInApp -> PushRouting.Open(routesAboveToday(target))
}

/**
 * The screens a target stacks above Today, so Back walks down the same steps as if the
 * person had gone there by hand: the Streak sits above the Archive (TodayNavigation).
 */
internal fun routesAboveToday(target: PushTarget): List<Any> = when (target) {
    PushTarget.Today -> emptyList()
    PushTarget.Archive -> listOf(ArchiveRoute)
    PushTarget.Streak -> listOf(ArchiveRoute, StreakRoute)
    is PushTarget.BondSettings -> listOf(BondSettingsGraph(target.bondId))
}
