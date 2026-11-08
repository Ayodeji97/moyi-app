package com.moyi.app.domain.notification

/**
 * What a push is about, as the server names it: the backend's `PushCategory`, sent as the
 * push's `kind` (`FcmPushSender.payloadOf`: `data: {bondId, kind}`, ADR-0044).
 *
 * Not [NotificationCategory]: that is the six toggles a member sets, and one toggle
 * (`reveal`) governs two kinds of push ([Reveal] and [SoloUnlock]).
 *
 * Every name the phone uses for a kind is derived from [wireName] the way the server
 * derives it, so the two cannot drift apart one key at a time:
 * - [channelId], the Android notification channel: the name in lower case, as backend
 *   issue #134 proposes for `android.notification.channel_id`;
 * - [titleLocKey] and [bodyLocKey], the string resources the system shows
 *   (`FcmPushSender.locKeys`: `push_<name in lower case>_title` and `_body`).
 */
enum class PushKind(val wireName: String) {
    /** The daily reminder to write. */
    Reminder("REMINDER"),

    /** The other person accepted the invite. */
    PartnerJoined("PARTNER_JOINED"),

    /** The day's entries opened. */
    Reveal("REVEAL"),

    /** A day that closed with one entry, opened to the other member: worded as an opening, never a miss (FR-076). */
    SoloUnlock("SOLO_UNLOCK"),

    /** 7, 30, 100 or 365 days written together (ADR-0041). */
    Milestone("MILESTONE"),

    /** An earlier year's day on this date (FR-092). */
    OnThisDay("ON_THIS_DAY"),

    /** A change the other member proposed, waiting on this one (Q13). */
    ProposalOpened("PROPOSAL_OPENED"),
    ;

    val channelId: String get() = wireName.lowercase()

    val titleLocKey: String get() = "push_${channelId}_title"

    val bodyLocKey: String get() = "push_${channelId}_body"

    companion object {
        /** The kind a push names, or null for one this build does not know (a newer server). */
        fun fromWire(name: String?): PushKind? = entries.firstOrNull { it.wireName == name?.trim() }
    }
}

/**
 * Where a tapped push opens the app. Decided from the push alone, but only a direction:
 * every screen still loads what it shows from the server as the signed-in person, so a
 * push naming a bond this person is not in opens nothing of that bond.
 */
sealed interface PushTarget {
    /** Today, the root of the signed-in app. A reveal plays over it when the day has opened. */
    data object Today : PushTarget

    /** The Archive's list. */
    data object Archive : PushTarget

    /** The Streak screen, above the Archive (its only parent). */
    data object Streak : PushTarget

    /** Bond settings for [bondId], where a proposal is answered. */
    data class BondSettings(val bondId: String) : PushTarget
}

/**
 * Reads a tapped push. The keys are the push's `data`, which reach the app as the tray
 * notification's intent extras on Android and as top-level keys of `userInfo` on iOS.
 */
object PushPayload {
    const val KEY_BOND_ID: String = "bondId"
    const val KEY_KIND: String = "kind"

    /**
     * The screen a tap on a push carrying [data] opens, or null when [data] is not a push
     * at all (an ordinary launch: neither key present).
     *
     * A push the app cannot read (an unknown kind, a missing or malformed bond id) still
     * opens the app, on Today: the person tapped Moyi, and Today is where Moyi starts.
     *
     * The payload carries no date (only `bondId` and `kind`), so a reveal, a milestone or
     * an on-this-day push cannot open its own day: see docs/backend-contract-gaps.md.
     */
    fun targetOf(data: Map<String, String?>): PushTarget? {
        val rawKind = data[KEY_KIND]
        val rawBondId = data[KEY_BOND_ID]
        if (rawKind == null && rawBondId == null) return null
        val bondId = rawBondId?.trim()?.takeIf(::isUuid)
        return when (PushKind.fromWire(rawKind)) {
            PushKind.Reminder, PushKind.PartnerJoined, PushKind.Reveal, PushKind.SoloUnlock, null -> PushTarget.Today
            PushKind.Milestone -> PushTarget.Streak
            PushKind.OnThisDay -> PushTarget.Archive
            PushKind.ProposalOpened -> bondId?.let(PushTarget::BondSettings) ?: PushTarget.Today
        }
    }

    /**
     * A bond id goes into a route and from there into a request path, so only the shape
     * the server sends (a UUID) is let through.
     */
    private fun isUuid(value: String): Boolean = UUID.matches(value)

    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
}
