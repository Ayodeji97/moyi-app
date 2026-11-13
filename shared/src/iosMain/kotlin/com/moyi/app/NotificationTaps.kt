package com.moyi.app

import com.moyi.app.domain.notification.PushPayload
import org.koin.mp.KoinPlatform

/**
 * Swift's `UNUserNotificationCenterDelegate` calls this when the person taps a notification
 * (`didReceive`, the default action). [userInfo] is the notification's `userInfo`: the
 * push's `data` keys sit at its top level, beside `aps` (FCM puts them there on iOS).
 *
 * Only the two keys the app reads are taken out, and nothing of it is logged. Starts Koin
 * if it is not running yet, as [registerBackgroundTasks] does: a tap that launched the app
 * may arrive before the view controller is built.
 */
fun notificationTapped(userInfo: Map<Any?, *>) {
    startKoinOnce()
    KoinPlatform.getKoin().get<PushTapInbox>().tapped(
        mapOf(
            PushPayload.KEY_BOND_ID to (userInfo[PushPayload.KEY_BOND_ID] as? String),
            PushPayload.KEY_KIND to (userInfo[PushPayload.KEY_KIND] as? String),
        ),
    )
}
