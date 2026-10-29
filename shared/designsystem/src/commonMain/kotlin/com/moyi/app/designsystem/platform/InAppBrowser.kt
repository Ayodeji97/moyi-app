package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable

/**
 * Opens a web page over the app without leaving it (D8): Chrome Custom Tabs on
 * Android, `SFSafariViewController` on iOS. The person reads the page in a
 * browser they trust, with its own address bar and their cookies, and one tap
 * returns them to where they were.
 *
 * For pages the app does not own the content of, such as the Terms and the
 * Privacy Policy. Only `https` and `http` URLs open; anything else is ignored,
 * because `SFSafariViewController` throws on any other scheme.
 */
fun interface InAppBrowser {
    fun open(url: String)
}

/** The platform's [InAppBrowser], tinted with the Rosewood ground and ink. */
@Composable
expect fun rememberInAppBrowser(): InAppBrowser

/** True for an absolute `http` or `https` URL with a host: the only kind either browser opens. */
internal fun isWebUrl(url: String): Boolean {
    val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
    val host = url.substringAfter("://", missingDelimiterValue = "").substringBefore('/')
    return (scheme == "https" || scheme == "http") && host.isNotBlank()
}
