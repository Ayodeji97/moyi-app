package com.moyi.app.data.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The bytes a [SecureTokenStore] encrypts. A data-layer class of its own, not
 * a network DTO: the storage format changes on our schedule, not the API's.
 * [version] lets a later build read (or deliberately drop) an older payload.
 */
@Serializable
internal class StoredSessionPayload(
    val version: Int = CURRENT_VERSION,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val status: String,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

internal object StoredSessionCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true // so [StoredSessionPayload.version] is always written
    }

    fun encode(session: StoredSession): String = json.encodeToString(
        StoredSessionPayload.serializer(),
        StoredSessionPayload(
            refreshToken = session.refreshToken,
            userId = session.userId,
            email = session.email,
            displayName = session.displayName,
            emailVerified = session.emailVerified,
            status = session.status,
        ),
    )

    /** Null for anything unreadable or from an unknown version; the caller then wipes and signs out. */
    fun decode(text: String): StoredSession? {
        val payload = try {
            json.decodeFromString(StoredSessionPayload.serializer(), text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (payload.version != StoredSessionPayload.CURRENT_VERSION) return null
        return StoredSession(
            refreshToken = payload.refreshToken,
            userId = payload.userId,
            email = payload.email,
            displayName = payload.displayName,
            emailVerified = payload.emailVerified,
            status = payload.status,
        )
    }
}
