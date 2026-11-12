package com.moyi.app

import org.koin.mp.KoinPlatform
import platform.Foundation.NSBundle

/** The Info.plist key the "API Base URL" build phase writes (iosApp/scripts/api-base-url.sh). */
private const val API_BASE_URL_KEY = "MoyiApiBaseUrl"

/**
 * Starts Koin the first time any iOS entry point needs it, talking to the base URL this
 * build was made with. Every entry point Swift calls goes through here: a background task,
 * a push token, a notification tap or a link can each arrive before the view controller is
 * built, and a second startKoin throws.
 *
 * The URL is read from the app's Info.plist, where the build phase put the one for this
 * configuration: a local backend for Debug (refusing production), production for Release.
 */
internal fun startKoinOnce() {
    if (KoinPlatform.getKoinOrNull() == null) initKoin(apiBaseUrl = bundledApiBaseUrl())
}

private fun bundledApiBaseUrl(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey(API_BASE_URL_KEY) as? String)
        ?.takeIf { it.isNotBlank() }
        ?: error(
            "Info.plist has no $API_BASE_URL_KEY: the app was built without the " +
                "\"API Base URL\" build phase (iosApp/scripts/api-base-url.sh).",
        )
