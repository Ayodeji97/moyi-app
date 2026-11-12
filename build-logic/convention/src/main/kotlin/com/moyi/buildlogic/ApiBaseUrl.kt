package com.moyi.buildlogic

import org.gradle.api.GradleException
import java.net.URI

/**
 * The one URL a release build talks to (CLAUDE.md, Configuration). Only the Android
 * `release` build type uses it; iOS Release has the same literal in
 * `iosApp/scripts/api-base-url.sh`, and CI checks both.
 */
const val PRODUCTION_API_BASE_URL = "https://api.getmoyi.app/api/v1"

/** Where a debug build points when nothing overrides it: the Mac, seen from the Android emulator. */
const val ANDROID_DEBUG_API_BASE_URL = "http://10.0.2.2:8080/api/v1"

/**
 * Returns [url] if a development build may use it, and fails the build otherwise: a
 * malformed URL, one not in the `/api/v1` form, or one that points at production. Real
 * people's data lives on getmoyi.app; a development build that talks to it can write into
 * a real account. [source] names where the value came from, for the message.
 */
fun checkedDevelopmentBaseUrl(url: String, source: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val host = uri?.host
    if (uri == null || uri.scheme !in setOf("http", "https") || host == null) {
        throw GradleException("moyi.apiBaseUrl from $source is not an http(s) URL with a host: '$url'")
    }
    // The same list guards the Android debug cleartext allowlist (ProductionHosts.kt).
    if (isProductionHost(host)) {
        throw GradleException(
            "moyi.apiBaseUrl from $source points at production ($host).\n" +
                "Development builds never talk to getmoyi.app: real people's data lives there " +
                "(CLAUDE.md, Configuration). Point it at a local backend, e.g. http://localhost:8080/api/v1, " +
                "or remove the override. Only a release build talks to production.",
        )
    }
    if (!uri.path.trimEnd('/').endsWith("/api/v1")) {
        throw GradleException(
            "moyi.apiBaseUrl from $source must end in /api/v1, the same form as Bruno and doc 06: '$url'",
        )
    }
    return url
}
