package com.moyi.app

import android.app.Application
import org.koin.android.ext.koin.androidContext

/**
 * Starts Koin once per process, before any Activity, with the context the Keystore store needs,
 * creates the notification channels, and starts push when Firebase is configured.
 */
class MoyiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Local backend in debug, production in release (androidApp/build.gradle.kts).
        initKoin(apiBaseUrl = BuildConfig.API_BASE_URL) { androidContext(this@MoyiApplication) }
        // Before any push can arrive: FCM shows a push only in a channel that exists.
        NotificationChannels.ensure(this)
        // After Koin: the token goes to PushTokenHolder. Does nothing without google-services.json.
        FirebasePush.start(this)
    }
}
