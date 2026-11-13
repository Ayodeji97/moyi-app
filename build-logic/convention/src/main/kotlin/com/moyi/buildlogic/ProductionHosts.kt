package com.moyi.buildlogic

/**
 * The hosts a development build must never talk to (CLAUDE.md, Configuration): real
 * people's data lives there. One list for every guard that needs it: the debug API base
 * URL check (ApiBaseUrl.kt, androidApp) and the debug cleartext allowlist (androidApp).
 * iosApp/scripts/api-base-url.sh repeats it for iOS Debug builds: change both together.
 */
private val productionIps = setOf("91.98.71.136") // moyi-prod-1

/** True for getmoyi.app, any of its subdomains, and production's IP addresses. */
fun isProductionHost(host: String): Boolean {
    // trimEnd('.'): "getmoyi.app." is the same host to DNS, and must not slip past.
    val h = host.lowercase().trimEnd('.')
    return h == "getmoyi.app" || h.endsWith(".getmoyi.app") || h in productionIps
}
