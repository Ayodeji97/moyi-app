package com.moyi.app.designsystem.platform

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.moyi.app.designsystem.MoyiTheme

/**
 * Chrome Custom Tabs. When no installed browser supports them, `launchUrl`
 * falls back to a plain browser intent; with no browser at all, nothing opens.
 */
@Composable
actual fun rememberInAppBrowser(): InAppBrowser {
    val context = LocalContext.current
    val surface = MoyiTheme.colors.surface.toArgb()
    return remember(context, surface) {
        InAppBrowser { url ->
            if (!isWebUrl(url)) return@InAppBrowser
            val colors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(surface)
                .setNavigationBarColor(surface)
                .build()
            val intent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colors)
                .setShowTitle(true)
                .build()
            try {
                intent.launchUrl(context, url.toUri())
            } catch (_: ActivityNotFoundException) {
                // No browser on the device: nothing to open the page in.
            }
        }
    }
}
