package com.moyi.app.data.network

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.FieldError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads a non-2xx response into an [ApiProblem], whatever its status.
 *
 * It decodes through a [JsonObject] rather than the generated `ProblemDetail`,
 * because:
 * - several refusals are not declared in the contract (login's 401, refresh's
 *   401, verify-email's 410: docs/backend-contract-gaps.md rows 1 to 3), so no
 *   generated type is tied to them;
 * - the generated `ProblemDetail.Code` throws on a code this build does not
 *   know, where we want [ApiErrorCode.Unknown] and the rest of the body.
 *
 * It never throws: a body that is not a problem yields [ApiErrorCode.Unknown]
 * with the HTTP status.
 */
object ProblemDetailParser {
    fun parse(status: Int, body: String, contentType: String?): ApiProblem {
        val unknown = ApiProblem(status = status, code = ApiErrorCode.Unknown)
        if (body.isBlank() || !isJson(contentType)) return unknown
        val json = try {
            MoyiJson.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return unknown

        val rawCode = json.string("code")
        return ApiProblem(
            status = status,
            code = ApiErrorCode.entries.find { it != ApiErrorCode.Unknown && it.name == rawCode } ?: ApiErrorCode.Unknown,
            fieldErrors = (json["errors"] as? JsonArray).orEmpty().mapNotNull { it.toFieldError() },
            traceId = json.string("traceId"),
        )
    }

    /** No content type: try anyway. Otherwise only JSON media types (`application/json`, `…+json`). */
    private fun isJson(contentType: String?): Boolean {
        if (contentType == null) return true
        val mediaType = contentType.substringBefore(';').trim().lowercase()
        return mediaType == "application/json" || mediaType.endsWith("+json")
    }

    private fun JsonElement.toFieldError(): FieldError? {
        val obj = this as? JsonObject ?: return null
        return FieldError(
            field = obj.string("field") ?: return null,
            code = obj.string("code") ?: return null,
            message = obj.string("message").orEmpty(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
