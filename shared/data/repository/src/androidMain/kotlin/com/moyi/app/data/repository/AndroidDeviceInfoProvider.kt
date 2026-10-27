package com.moyi.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

class AndroidDeviceInfoProvider(private val context: Context) : DeviceInfoProvider {
    override fun deviceInfo(): DeviceInfo = DeviceInfo(
        platform = "ANDROID",
        appVersion = appVersion(),
        osVersion = Build.VERSION.RELEASE ?: "unknown",
    )

    private fun appVersion(): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return info.versionName ?: "unknown"
    }
}

actual val platformRepositoryModule: Module = module {
    single<DeviceInfoProvider> { AndroidDeviceInfoProvider(androidContext()) }
}
