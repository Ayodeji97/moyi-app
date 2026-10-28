package com.moyi.app.feature.auth

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.FieldError

fun apiError(status: Int, code: ApiErrorCode, vararg fields: FieldError): DataError.Network.Api =
    DataError.Network.Api(ApiProblem(status = status, code = code, fieldErrors = fields.toList()))

fun violation(field: String, code: String) = FieldError(field = field, code = code, message = "server prose, never shown")
