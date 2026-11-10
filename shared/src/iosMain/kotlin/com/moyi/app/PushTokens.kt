package com.moyi.app

import com.moyi.app.data.repository.PushTokenHolder
import org.koin.mp.KoinPlatform

/**
 * Swift's `MessagingDelegate` calls this with this device's FCM token
 * (`messaging(_:didReceiveRegistrationToken:)` in `NotificationDelegate.swift`): at the
 * first launch once the APNs token reached FCM, and again whenever FCM rotates it. Null
 * means FCM has none.
 *
 * The token goes to [PushTokenHolder], which `PushTokenRegistrar` watches to tell the
 * server. It is a secret device identifier: never logged. Starts Koin if it is not running
 * yet, as [notificationTapped] does: FCM can answer before the view controller is built.
 */
fun pushTokenReceived(token: String?) {
    if (KoinPlatform.getKoinOrNull() == null) initKoin()
    KoinPlatform.getKoin().get<PushTokenHolder>().update(token)
}
