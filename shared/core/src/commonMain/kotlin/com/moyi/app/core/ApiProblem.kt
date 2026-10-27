package com.moyi.app.core

/**
 * The app's view of a backend problem response (RFC 9457, doc 06 §2).
 *
 * App-owned rather than the generated `ProblemDetail`, for two reasons: domain
 * and feature code may not see the generated client, and decoding the
 * generated type throws on a code this build does not know. [code] is
 * [ApiErrorCode.Unknown] for such a code, or when the body was not a problem.
 *
 * @property status the HTTP status of the response, which wins over the body's.
 * @property traceId present once the backend adds it to the contract
 *   (docs/backend-contract-gaps.md, row 6); worth quoting in a bug report.
 */
data class ApiProblem(
    val status: Int,
    val code: ApiErrorCode,
    val fieldErrors: List<FieldError> = emptyList(),
    val traceId: String? = null,
)

/** One rejected request field. The server never echoes the rejected value. */
data class FieldError(
    val field: String,
    val code: String,
    val message: String,
)
