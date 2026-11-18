package com.moyi.app.data.network

import kotlin.test.Test
import kotlin.test.assertEquals

class RedactQueriesTest {
    @Test
    fun cutsTheQueryOfEveryUrlInALogLine() {
        assertEquals(
            "REQUEST: http://localhost:8080/api/v1/bonds/b1/search?… METHOD: GET",
            redactQueries("REQUEST: http://localhost:8080/api/v1/bonds/b1/search?q=my%20secret%20words METHOD: GET"),
        )
    }

    @Test
    fun leavesAUrlWithoutAQueryAlone() {
        val line = "RESPONSE: 200 OK METHOD: POST FROM: http://localhost:8080/api/v1/auth/login"
        assertEquals(line, redactQueries(line))
    }

    @Test
    fun cutsTheQuestionOfRememberWhen() {
        assertEquals(
            "REQUEST: https://api.getmoyi.app/api/v1/bonds/b1/remember?… METHOD: GET",
            redactQueries("REQUEST: https://api.getmoyi.app/api/v1/bonds/b1/remember?q=when%20I%20was%20unwell&limit=10 METHOD: GET"),
        )
    }
}
