package com.moyi.app.feature.today.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Every layout Today can take (states.md §3; Figma section 425:314).
 *
 * Mapped from the API's `bondDay.status` (`OPEN · PARTIAL · PENDING_REVEAL ·
 * REVEALED · SOLO · EMPTY · SUSPENDED · FROZEN`) plus whether *I* have written,
 * never whether the partner has:
 *
 * - OPEN, I have not written → [NotWritten], **whether or not the partner wrote**.
 * - PARTIAL with my entry, and PENDING_REVEAL → [Waiting] (states.md §5a: never Revealed).
 * - REVEALED → [Revealed], or [Tombstone] once my revealed entry is deleted.
 * - SOLO → [SoloYouWrote] / [SoloTheyWrote].
 *
 * TODO(logic): the mapping above, in a presentation mapper over the domain's Today.
 * EMPTY, SUSPENDED and FROZEN have no Today frame yet (recorded in docs/figma/today-check.md).
 */
enum class TodayStatus {
    /** 159:2. Also what "the partner wrote, I haven't" renders as. */
    NotWritten,

    /** 159:9. */
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

/** One day, as the fake loop holds it. */
data class FakeDay(
    val status: TodayStatus,
    val myEntry: String,
    val partnerEntry: String,
)

/**
 * THE FAKE BACKEND for PR #8 ("no logic, fake data"). One instance per app (Koin
 * `single`), so the screens share a day: sending from Write moves Today to waiting,
 * the reveal settles it into revealed, and so on, all without a network.
 *
 * TODO(logic): replace with a `TodayRepository` in :shared:domain, backed by
 *  - `GET /api/v1/bonds/{bondId}/today` (cache first, refresh behind; states.md §3:
 *    "renders from cache instantly"),
 *  - `POST /api/v1/bonds/{bondId}/entries` (Idempotency-Key header),
 *  - `PATCH /api/v1/entries/{entryId}` (Idempotency-Key optional) and
 *    `DELETE /api/v1/entries/{entryId}` (no Idempotency-Key),
 *  and the local draft and outbox tables (doc 13 §5).
 */
class FakeDailyLoop {
    private val _day = MutableStateFlow(
        FakeDay(status = TodayStatus.NotWritten, myEntry = FakeCopy.MY_ENTRY, partnerEntry = FakeCopy.PARTNER_ENTRY),
    )
    val day: StateFlow<FakeDay> = _day.asStateFlow()

    fun setStatus(status: TodayStatus) = _day.update { it.copy(status = status) }

    fun setMyEntry(text: String, status: TodayStatus) = _day.update { it.copy(myEntry = text, status = status) }

    /** The debug switch: the next layout in [TodayStatus] order, wrapping. */
    fun nextStatus(): TodayStatus {
        val all = TodayStatus.entries
        val next = all[(all.indexOf(_day.value.status) + 1) % all.size]
        setStatus(next)
        return next
    }
}

/**
 * The fake day's content. Not user-facing copy: stand-ins for the server's data
 * (a member's words, a name, a server prompt, a formatted date), so they are not
 * string resources.
 *
 * TODO(logic): every value here comes from the API: `partnerName` from the bond's
 * members, `prompt.text` from `GET …/today`, the dates from `bondDay.date` formatted
 * by locale (day-first, "Tuesday · 2 September"), the entries from `myEntry` /
 * `partnerEntry`.
 */
object FakeCopy {
    const val PARTNER_NAME = "Tunde"
    const val DATE = "Tuesday · 2 September"
    const val SOLO_DATE = "Monday · 1 September"

    /** The same day in running text ("It will count for Tuesday, 2 September.", states.md §10). */
    const val DATE_IN_SENTENCE = "Tuesday, 2 September"
    const val PROMPT = "What did Tunde do today that you noticed?"
    const val MY_ENTRY = "The way you sat on the phone with me until I fell asleep."
    const val PARTNER_ENTRY =
        "You stayed up to help me finish it, and never once made me feel slow. I noticed. I always notice."
}

/** How long a fake call takes, so Sending and Deleting are visible on a device. Not a design value. */
val FakeLatency: Duration = 800.milliseconds
