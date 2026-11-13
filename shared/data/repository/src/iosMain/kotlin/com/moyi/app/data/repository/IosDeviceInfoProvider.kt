package com.moyi.app.data.repository

import com.moyi.app.api.models.DeviceRequest
import com.moyi.app.domain.notification.NotificationAskMemory
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

class IosDeviceInfoProvider : DeviceInfoProvider {
    override fun deviceInfo(): DeviceInfo = DeviceInfo(
        platform = DeviceRequest.Platform.IOS,
        appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown",
        osVersion = UIDevice.currentDevice.systemVersion,
    )
}

actual val platformRepositoryModule: Module = module {
    single<DeviceInfoProvider> { IosDeviceInfoProvider() }
    single<OutboxScheduler> { IosOutboxScheduler() }
    single<ConnectivityWatcher> { IosConnectivityWatcher() }
    // "Not now" to Today's notification note: per phone, in NSUserDefaults, kept across sign-out.
    single<NotificationAskMemory> { UserDefaultsNotificationAskMemory() }
}
