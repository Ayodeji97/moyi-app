package com.moyi.app

import com.moyi.app.data.repository.PushTokenHolder
import org.koin.mp.KoinPlatform

/**
 * The Android shell calls this with this device's FCM token: `MoyiMessagingService.onNewToken`
 * when FCM issues or rotates one, and `FirebasePush.start` with the current one at launch.
 *
 * The token goes to [PushTokenHolder], which `PushTokenRegistrar` watches to tell the
 * server. It is a secret device identifier: never logged. Koin is always running here:
 * `MoyiApplication.onCreate` starts it before any service or activity is created.
 */
fun pushTokenReceived(token: String?) {
    KoinPlatform.getKoin().get<PushTokenHolder>().update(token)
}
