package com.moyi.app.domain.report

import com.moyi.app.core.DataError
import com.moyi.app.core.Result

/**
 * Why something is reported (FR-100, ADR-0053 decisions 3 and 14). Every value the
 * contract's `ReportRequest.reason` declares, the same five for an entry and for the
 * member. The names are the wire's; the words a person reads are the presentation
 * layer's (doc 26 §6 proposed four other values; the backend chose these, and the
 * contract wins: docs/backend-contract-gaps.md row 36).
 *
 * `MINOR_SEXUAL_CONTENT` is reviewed first by the server. `HARASSMENT` is T-09's
 * "how they're treating me".
 */
enum class ReportReason { NON_CONSENSUAL, MINOR_SEXUAL_CONTENT, VIOLENCE_OR_SELF_HARM, HARASSMENT, OTHER }

/** What a report is about: one entry of the partner's, or the partner themselves. */
sealed interface ReportTarget {
    /**
     * The partner's entry [entryId], one the caller was shown: read in full, or its
     * tombstone. My own entry is `409 REPORT_ON_OWN_ENTRY`; a locked one is the `404`.
     */
    data class Entry(val entryId: String) : ReportTarget

    /** The other member of [bondId] (`…/members/other/report`): allowed in every state of the bond. */
    data class Member(val bondId: String) : ReportTarget
}

/** What the server says it filed: its id and the reason it recorded. Never what is decided. */
data class FiledReport(val id: String, val reason: ReportReason)

/**
 * Reports, to a person (ADR-0053). Every call needs a signed-in member.
 *
 * [idempotencyKey] is minted by the caller at the Send tap and passed again, unchanged,
 * on every retry of **the same** report (doc 06 §1): a retry after a lost answer is the
 * same report, never a second. A report with a different reason or detail needs a new
 * key, because the server binds the key to the body (`422` otherwise).
 *
 * The refusals a caller can meet, read from [DataError.Network.Api]:
 * - `409 REPORT_ON_OWN_ENTRY`: the entry is the caller's.
 * - `404`: an entry the caller was never shown, a bond they are not in, or one that
 *   never had a second member. One answer for all of them, by design.
 * - `422 VALIDATION_FAILED`: a `detail` that is blank or longer than [MAX_DETAIL] code points.
 * - `429 RATE_LIMITED`: ten reports a day, across both targets.
 * - `409 IDEMPOTENCY_KEY_IN_FLIGHT`: the same report is still being filed; retry.
 *
 * The reported member is never told, by any field, push or code (doc 26 §2.2).
 */
interface ReportRepository {
    suspend fun report(
        target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        idempotencyKey: String,
    ): Result<FiledReport, DataError.Network>

    companion object {
        /** `ReportRequest.detail`'s `maxLength`, in Unicode code points. */
        const val MAX_DETAIL = 500
    }
}

/**
 * [text] as the `detail` to send: null when there is nothing in it but white space,
 * since the server refuses a blank one but takes an absent one (`ReportRequest.detail`).
 * Otherwise sent exactly as written.
 */
fun reportDetailOrNull(text: String): String? = text.takeIf { it.isNotBlank() }

/**
 * The number of Unicode code points in [text], which is what the server counts
 * (`ReportDetail`), not UTF-16 units: an emoji is one, though it is two `Char`s.
 */
fun codePointCount(text: String): Int {
    var count = 0
    var i = 0
    while (i < text.length) {
        val high = text[i]
        i += if (high.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return count
}

/** True when [text] is longer than the server takes as a `detail`. */
fun isReportDetailTooLong(text: String): Boolean = codePointCount(text) > ReportRepository.MAX_DETAIL
