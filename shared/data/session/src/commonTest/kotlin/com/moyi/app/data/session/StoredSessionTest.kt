package com.moyi.app.data.session

import com.moyi.app.data.testing.storedSession
import com.moyi.app.domain.auth.User
import com.moyi.app.domain.auth.UserStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredSessionTest {
    private val secret = "rt_9f8e7d6c5b4a-very-secret"

    @Test
    fun toStringNeverShowsTheRefreshToken() {
        val text = storedSession(refresh = secret).toString()
        assertFalse(secret in text, text)
        assertTrue("<redacted>" in text, text)
        // And the person's email and name stay out of logs too.
        assertFalse("ada@example.test" in text, text)
    }

    @Test
    fun sessionGrantToStringShowsNeitherToken() {
        val grant = SessionGrant(
            accessToken = "eyJ.access.secret",
            refreshToken = secret,
            user = User("u1", "ada@example.test", "Ada", true, UserStatus.ACTIVE),
        )
        assertFalse(secret in grant.toString())
        assertFalse("eyJ.access.secret" in grant.toString())
    }

    @Test
    fun codecRoundTrips() {
        val session = storedSession(refresh = secret)
        assertEquals(session, StoredSessionCodec.decode(StoredSessionCodec.encode(session)))
    }

    @Test
    fun codecRejectsGarbageAndUnknownVersions() {
        assertNull(StoredSessionCodec.decode("not json"))
        assertNull(StoredSessionCodec.decode("""{"version":1}"""))
        val encoded = StoredSessionCodec.encode(storedSession())
        assertTrue("\"version\":1" in encoded, "the version must be written: $encoded")
        assertNull(StoredSessionCodec.decode(encoded.replace("\"version\":1", "\"version\":2")))
    }
}
