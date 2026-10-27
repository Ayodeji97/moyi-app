package com.moyi.app.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApiOriginTest {
    @Test
    fun stripsApiV1() {
        assertEquals("http://10.0.2.2:8080", apiOrigin("http://10.0.2.2:8080/api/v1"))
        assertEquals("http://localhost:8080", apiOrigin("http://localhost:8080/api/v1"))
    }

    @Test
    fun stripsApiV1WithTrailingSlash() {
        assertEquals("http://localhost:8080", apiOrigin("http://localhost:8080/api/v1/"))
    }

    @Test
    fun keepsAPathPrefixInFrontOfApiV1() {
        assertEquals("https://dev.example.test/moyi", apiOrigin("https://dev.example.test/moyi/api/v1"))
    }

    @Test
    fun refusesABaseUrlWithoutApiV1() {
        val error = assertFailsWith<IllegalArgumentException> { apiOrigin("http://localhost:8080") }
        assertTrue("/api/v1" in error.message.orEmpty())
    }

    @Test
    fun refusesOtherVersionsAndLookalikes() {
        assertFailsWith<IllegalArgumentException> { apiOrigin("http://localhost:8080/api/v2") }
        assertFailsWith<IllegalArgumentException> { apiOrigin("http://localhost:8080/api/v10") }
        assertFailsWith<IllegalArgumentException> { apiOrigin("http://localhost:8080/api/v1/auth") }
    }

    @Test
    fun refusesAUrlWithNoHost() {
        assertFailsWith<IllegalArgumentException> { apiOrigin("/api/v1") }
        assertFailsWith<IllegalArgumentException> { apiOrigin("http:///api/v1") }
    }

    @Test
    fun apiConfigExposesTheOrigin() {
        assertEquals("http://localhost:8080", ApiConfig("http://localhost:8080/api/v1").origin)
    }

    @Test
    fun buildConfigIsALocalUrlInApiV1Form() {
        val config = ApiConfig.fromBuild()
        assertTrue(config.baseUrl.trimEnd('/').endsWith("/api/v1"), config.baseUrl)
        assertTrue("getmoyi.app" !in config.baseUrl, config.baseUrl)
    }
}
