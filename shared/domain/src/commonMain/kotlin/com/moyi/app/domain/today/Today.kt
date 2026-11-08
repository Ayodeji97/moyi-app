package com.moyi.app.domain.today

/**
 * One Bond-day as `GET /bonds/{bondId}/today` reports it, for the person asking.
 * Built from the server's `TodayResponse` by the data layer; nothing here knows
 * the wire format.
 *
 * [date] is the Bond-local date in ISO form (`2026-09-14`, doc 06 §1). It stays a
 * string here because the domain depends on no library beyond the standard one and
 * coroutines (CLAUDE.md, the dependency guard); the presentation layer parses it.
 *
 * The streak the response also carries is not modelled: Today never shows it.
 */
data class Today(
    val date: String,
    val status: BondDayStatus,
    /** Mine, in full or as its tombstone; null until I have written. */
    val myEntry: MyEntry?,
    /** The other member's, in whatever shape the server let me see; null when there is none to report. */
    val partnerEntry: PartnerEntry?,
    /** The bond's prompt of the day; null when the bond takes no writes, or the library has none. */
    val prompt: Prompt?,
)

/** `bondDay.status` (doc 04, backend `BondDayStatus`): every value the contract declares. */
enum class BondDayStatus {
    /** Nobody has written (or every entry was withdrawn). */
    OPEN,

    /** One entry so far. It may be the partner's: what *I* see depends on [Today.myEntry], never on this alone. */
    PARTIAL,

    /** Both have written; the reveal time has not come (states.md §5a: never shown as revealed). */
    PENDING_REVEAL,
    REVEALED,

    /** Closed at rollover with one entry, unlocked for both (FR-063). */
    SOLO,

    /** Closed with none. */
    EMPTY,

    /** No one is being asked yet: the bond is waiting for its second member (doc 04 §8.3a). */
    SUSPENDED,

    /** Closed by a streak freeze. */
    FROZEN,
}

/**
 * My own entry. [text] is null exactly when [status] is [EntryStatus.DELETED]:
 * the tombstone of an entry I withdrew.
 */
data class MyEntry(
    val id: String,
    val text: String?,
    val status: EntryStatus,
    /** The photo on it, as the server shows it to me; null when it has none, or when read from the cache. */
    val image: EntryImage? = null,
    /** The voice note on it, as the server plays it to me; null when it has none, or when read from the cache. */
    val voice: EntryVoice? = null,
) {
    /** Written and not withdrawn. */
    val isLive: Boolean get() = status != EntryStatus.DELETED

    /** Never the words: a data class would print them into any log that printed this. */
    override fun toString(): String = "MyEntry(id=$id, text=${if (text == null) "null" else "(redacted)"}, status=$status, image=$image, voice=$voice)"
}

/** An entry's own status, as an entry read in full carries it. */
enum class EntryStatus { SUBMITTED, REVEALED, DELETED }

/**
 * The partner's entry, mirroring the contract's `oneOf` on `status`. Only the
 * shapes that carry words have a [text]: a locked entry carries the author and
 * nothing else (BR-8), so there is nothing here that could leak.
 */
sealed interface PartnerEntry {
    /** Read in full while its status is still SUBMITTED: a solo day unlocked at rollover (FR-063). */
    data class Submitted(
        val id: String,
        val text: String,
        val image: EntryImage? = null,
        val voice: EntryVoice? = null,
    ) : PartnerEntry {
        override fun toString(): String = "Submitted(id=$id, text=(redacted), image=$image, voice=$voice)"
    }

    /** Read in full: the day opened. */
    data class Revealed(
        val id: String,
        val text: String,
        val image: EntryImage? = null,
        val voice: EntryVoice? = null,
    ) : PartnerEntry {
        override fun toString(): String = "Revealed(id=$id, text=(redacted), image=$image, voice=$voice)"
    }

    /** Its tombstone: the partner deleted it after I had been shown it. */
    data class Deleted(val id: String) : PartnerEntry

    /**
     * Written and not yet revealed. An entry the partner erased before I ever saw it
     * is not a shape at all: the server sends no partner entry, as if it had never
     * been written (backend #101, the owner's ruling of 2026-10-21).
     */
    data object Locked : PartnerEntry
}

/** The prompt of the day (FR-047, ADR-0038). Optional to answer; dismissing it is the client's alone. */
data class Prompt(
    val id: String,
    val text: String,
)

/**
 * An entry's photo as the server shows it to the person asking (`EntryImageResponse`,
 * spec §4 D2). [url], [width] and [height] are present when [status] is
 * [EntryMediaStatus.Ready], and on [EntryMediaStatus.NotShared] unless the photo was
 * blocked outright. [sensitive] is true only on a ready photo that passed moderation with
 * adult nudity found: shown covered, behind a tap.
 *
 * The [url] is a signed GET, valid for at least 24 hours: a credential for the photo. It
 * is never logged ([toString]) and never written to the device's cache.
 */
data class EntryImage(
    val id: String,
    val status: EntryMediaStatus,
    val url: String?,
    val width: Int?,
    val height: Int?,
    val sensitive: Boolean,
) {
    override fun toString(): String =
        "EntryImage(id=$id, status=$status, url=${url?.let { "(redacted)" }}, width=$width, height=$height, sensitive=$sensitive)"
}

/**
 * An entry's voice note as the server plays it to the person asking (`EntryVoiceResponse`,
 * spec §4 D3). [url] and [durationMs] are present when [status] is [EntryMediaStatus.Ready],
 * and on [EntryMediaStatus.NotShared] unless it was blocked outright. [sensitive] as a photo's.
 *
 * The [url] is a signed GET of an Ogg Opus file (mono, 24 kbit/s), valid for at least 24
 * hours: a credential for the voice note. It is never logged ([toString]), never written to
 * the device's cache, and fetched only when the person taps play (metered data: voice notes
 * on demand).
 */
data class EntryVoice(
    val id: String,
    val status: EntryMediaStatus,
    val url: String?,
    val durationMs: Int?,
    val sensitive: Boolean,
) {
    override fun toString(): String =
        "EntryVoice(id=$id, status=$status, url=${url?.let { "(redacted)" }}, durationMs=$durationMs, sensitive=$sensitive)"
}

/** How an entry's photo or voice note stands for the person asking (`EntryImageResponse` and `EntryVoiceResponse` share it). */
enum class EntryMediaStatus {
    /** Shown or playable: its url reaches it. */
    Ready,

    /** Not shown to this reader yet. Never says why: not processed, not moderated, or kept from them. */
    Processing,

    /** Its author's alone: held back from the partner, who is shown [Processing] and never told (Q10). */
    NotShared,
}
