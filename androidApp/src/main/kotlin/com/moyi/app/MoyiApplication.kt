package com.moyi.app

import android.app.Application
import org.koin.android.ext.koin.androidContext

/** Starts Koin once per process, before any Activity, with the context the Keystore store needs. */
class MoyiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@MoyiApplication) }
    }
}
