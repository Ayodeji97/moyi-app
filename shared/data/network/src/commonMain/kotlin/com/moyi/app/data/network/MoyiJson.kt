package com.moyi.app.data.network

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration for the API.
 *
 * - `ignoreUnknownKeys`: v1 grows by adding fields (doc 06 §1); an older app
 *   must not fail on a newer server.
 * - `explicitNulls = false`: a null optional field is left out of a request
 *   rather than sent as `null`, and a missing field decodes to null.
 * - `coerceInputValues`: a null or unknown enum value for a property with a
 *   default takes the default instead of failing the whole response.
 */
val MoyiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
