package com.moyi.app.feature.auth.common

import androidx.compose.ui.text.intl.Locale

/** The device's language, as register sends it. An interface so tests can fix it. */
fun interface DeviceLocale {
    /** A tag the backend accepts: `^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$`. */
    fun languageTag(): String
}

/** Reads Compose Multiplatform's current locale, which follows the platform's on both targets. */
object PlatformDeviceLocale : DeviceLocale {
    override fun languageTag(): String = backendLanguageTag(Locale.current.toLanguageTag())
}

private val languageOnly = Regex("^[a-zA-Z]{2,3}$")
private val subtag = Regex("^[a-zA-Z0-9]{2,8}$")

/**
 * Fits a BCP 47 tag to the backend's two-part pattern.
 *
 * - `en`, `en-GB`, `pt-PT`: unchanged.
 * - `zh-Hans-CN`: keeps the language and the region, `zh-CN`, because the
 *   pattern allows one subtag and the region is what changes date formats.
 * - `sr-Latn`: `sr-Latn` (a script is a valid single subtag).
 * - Anything unreadable (empty, `und`, `C`): `en`, the launch locale.
 * - Underscores (`en_GB`, as some platforms write it) are read as hyphens.
 */
fun backendLanguageTag(tag: String): String {
    val parts = tag.replace('_', '-').split('-').filter { it.isNotEmpty() }
    val language = parts.firstOrNull()?.takeIf { languageOnly.matches(it) && !it.equals("und", ignoreCase = true) }
        ?: return "en"
    val rest = parts.drop(1).filter { subtag.matches(it) }
    // Prefer a region (2 letters or 3 digits) over a script (4 letters).
    val region = rest.firstOrNull { it.length == 2 || (it.length == 3 && it.all(Char::isDigit)) }
    val second = region ?: rest.firstOrNull()
    return if (second == null) language.lowercase() else "${language.lowercase()}-$second"
}
