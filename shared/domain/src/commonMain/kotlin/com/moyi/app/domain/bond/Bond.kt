package com.moyi.app.domain.bond

import kotlin.time.Instant

/**
 * A bond as the app needs it. Built from the server's `BondResponse` by the
 * data layer; nothing here knows the wire format.
 *
 * What onboarding routes on, plus the one thing Today needs: the other member's
 * name ([partnerName]).
 */
data class Bond(
    val id: String,
    val name: String,
    val type: BondType,
    val status: BondStatus,
    /** The live invite, while there is one (any member sees it, FR-022). */
    val invite: Invite?,
    /**
     * The other member's display name, from the bond's `members` (not `me`). Null
     * while nobody has joined, and when the server's member list does not say who
     * the caller is. A member who has left is still named: their words stay.
     */
    val partnerName: String? = null,
    /**
     * The IANA zone the bond's day turns over in (`Africa/Lagos`, doc 04 §3): the
     * Bond-day is the calendar date there. Null when the server did not say.
     */
    val anchorTimezone: String? = null,
) {
    /**
     * Open: the person is in it and it has not ended. A bond waiting for the
     * second person is open (the creator can already write, states.md §3), and
     * so is one in its deletion cooling-off (it can still be called off).
     */
    val isOpen: Boolean
        get() = status == BondStatus.PENDING_MEMBER || status == BondStatus.ACTIVE || status == BondStatus.PENDING_DELETION
}

/**
 * `Bond.type` (states.md §1a). Three cards map to the first three;
 * [PARENT_CHILD] has no card but the server may still send it.
 */
enum class BondType { COUPLE, FRIENDS, FAMILY, PARENT_CHILD }

/** `Bond.status` (doc 04). */
enum class BondStatus { PENDING_MEMBER, ACTIVE, ARCHIVED, PENDING_DELETION, DELETED }

/**
 * A live invite: the six-character [code] and the [link] that carries it (FR-022).
 * [expiresAt] is when it stops working; null when the server did not say.
 */
data class Invite(
    val id: String,
    val code: String,
    val link: String,
    val expiresAt: Instant? = null,
)

/** What `GET /invites/{code}` shows before the invite is spent (states.md §2, Confirm). */
data class InvitePreview(
    val inviterName: String,
    val bondName: String,
    val bondType: BondType,
)
