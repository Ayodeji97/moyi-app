package com.moyi.app.data.repository

import com.moyi.app.api.models.DeviceRequest
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
}
