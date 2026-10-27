package com.moyi.app.data.repository

import com.moyi.app.api.models.DeviceRequest

/**
 * What login tells the server about this device, shown later in the session
 * list (doc 06 §3.1, ADR-0025). [platform] is `ANDROID` or `IOS`: the
 * contract has a pattern rather than an enum (contract gap 8).
 */
data class DeviceInfo(
    val platform: String,
    val appVersion: String,
    val osVersion: String,
)

fun interface DeviceInfoProvider {
    fun deviceInfo(): DeviceInfo
}

internal fun DeviceInfo.toRequest(): DeviceRequest =
    DeviceRequest(appVersion = appVersion, osVersion = osVersion, platform = platform)
