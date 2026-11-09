package com.moyi.app

import org.koin.mp.KoinPlatform

/**
 * Swift calls this with a universal link that opened the app (`onContinueUserActivity`
 * with `NSUserActivityTypeBrowsingWeb`, in `iOSApp.swift`): `https://getmoyi.app/verify`,
 * `/reset-password` or `/i/…`, the paths the site's AASA lists (moyi-backend#157).
 *
 * The URL goes to [LinkInbox] as it is and is never logged: it may carry a token. Starts
 * Koin if it is not running yet, as [notificationTapped] does: a link that launched the
 * app can arrive before the view controller is built.
 */
fun linkOpened(url: String) {
    if (KoinPlatform.getKoinOrNull() == null) initKoin()
    KoinPlatform.getKoin().get<LinkInbox>().opened(url)
}
