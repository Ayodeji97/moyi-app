package com.moyi.app.feature.today.common

/**
 * Every layout Today can take (states.md §3; Figma section 425:314).
 *
 * Chosen from the API's `bondDay.status` plus whether *I* have a live entry, never
 * from whether the partner has written. [todayStatus] holds the table; in short:
 *
 * - OPEN, PARTIAL, PENDING_REVEAL, SUSPENDED: mine written → [Waiting] (or
 *   [PartnerNotJoined] while the bond waits for its second member); not written →
 *   [NotWritten], **whether or not the partner wrote**. PENDING_REVEAL is never
 *   [Revealed] (states.md §5a).
 * - REVEALED → [Revealed], or [Tombstone] once my revealed entry is deleted.
 * - SOLO → [SoloYouWrote] / [SoloTheyWrote].
 * - EMPTY, FROZEN: no frame exists (docs/figma/today-check.md, "Owed"); the most
 *   conservative existing layout stands in: [SoloYouWrote] if mine is there,
 *   [NotWritten] otherwise.
 *
 * [Queued] has no source yet: it needs the outbox (doc 13 §5). Only the UI preview reaches it.
 */
enum class TodayStatus {
    /** 159:2. Also what "the partner wrote, I haven't" renders as. */
    NotWritten,

    /** 159:9 (and PENDING_REVEAL). */
    Waiting,

    /** 159:19, the settled layout after the reveal. */
    Revealed,

    /** 420:54. Revealed, and I deleted mine. */
    Tombstone,

    /** 188:481. The creator wrote; the invitee has not joined. */
    PartnerNotJoined,

    /** 188:491. */
    SoloYouWrote,

    /** 188:501. */
    SoloTheyWrote,

    /** 384:327. My entry is on the device, waiting for a connection. */
    Queued,
}
