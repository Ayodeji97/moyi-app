package com.moyi.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultTest {
    @Test
    fun mapTransformsSuccess() {
        val result: Result<Int, DataError> = Result.Success(2)
        assertEquals(Result.Success(4), result.map { it * 2 })
    }

    @Test
    fun mapKeepsFailure() {
        val result: Result<Int, DataError> = Result.Failure(DataError.Network.NoInternet)
        assertEquals(Result.Failure(DataError.Network.NoInternet), result.map { it * 2 })
    }

    @Test
    fun onFailureRunsOnlyForFailure() {
        var seen: DataError? = null
        Result.Success(1).onFailure { seen = DataError.Local.UNKNOWN }
        assertEquals(null, seen)
        Result.Failure(DataError.Local.NOT_FOUND).onFailure { seen = it }
        assertTrue(seen == DataError.Local.NOT_FOUND)
    }
}
