package com.moyi.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiErrorCodeTest {
    @Test
    fun generatedEnumCarriesTheContractCodesAndUnknownLast() {
        assertTrue(ApiErrorCode.INVALID_CREDENTIALS in ApiErrorCode.entries)
        assertTrue(ApiErrorCode.TOKEN_REUSE_DETECTED in ApiErrorCode.entries)
        assertEquals(ApiErrorCode.Unknown, ApiErrorCode.entries.last())
    }
}
