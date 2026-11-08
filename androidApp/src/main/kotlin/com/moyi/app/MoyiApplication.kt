package com.moyi.app

import android.app.Application
import org.koin.android.ext.koin.androidContext

/**
 * Starts Koin once per process, before any Activity, with the context the Keystore store needs,
 * and creates the notification channels.
 */
class MoyiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@MoyiApplication) }
        // Before any push can arrive: FCM shows a push only in a channel that exists.
        NotificationChannels.ensure(this)
    }
}
