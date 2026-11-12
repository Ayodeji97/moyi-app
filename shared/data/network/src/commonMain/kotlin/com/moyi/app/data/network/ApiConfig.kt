package com.moyi.app.data.network

/**
 * Where the API lives.
 *
 * [baseUrl] keeps the form people write and Bruno uses, ending in `/api/v1`.
 * The generated client wants [origin] instead, because every generated path
 * already starts with `/api/v1` (docs/backend-contract-gaps.md, row 7).
 *
 * The app decides [baseUrl] per build, not this library (a library has no debug or
 * release variant): Android's `BuildConfig.API_BASE_URL`, iOS's `MoyiApiBaseUrl` in
 * Info.plist. Debug builds point at a local backend and fail to build for a production
 * host; release builds point at production (README, "Which backend the app talks to").
 */
data class ApiConfig(val baseUrl: String) {
    val origin: String = apiOrigin(baseUrl)
}

private const val API_PREFIX = "/api/v1"

/**
 * `http://10.0.2.2:8080/api/v1` (or with a trailing slash) becomes
 * `http://10.0.2.2:8080`, so `/api/v1` is never doubled.
 *
 * @throws IllegalArgumentException if [baseUrl] does not end in `/api/v1`:
 *   silently passing it through would send every request to the wrong path.
 */
fun apiOrigin(baseUrl: String): String {
    val withoutSlash = baseUrl.trimEnd('/')
    require(withoutSlash.endsWith(API_PREFIX)) {
        "API base URL must end in $API_PREFIX (the generated paths start with it), got '$baseUrl'"
    }
    val origin = withoutSlash.removeSuffix(API_PREFIX)
    require(origin.contains("://") && origin.substringAfter("://").isNotEmpty()) {
        "API base URL must have a scheme and a host before $API_PREFIX, got '$baseUrl'"
    }
    return origin
}
