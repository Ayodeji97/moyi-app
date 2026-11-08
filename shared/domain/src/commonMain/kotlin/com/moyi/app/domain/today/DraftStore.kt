package com.moyi.app.domain.today

/**
 * The words being written and not yet sent, kept on this device only (doc 13 §5:
 * drafts are local first, always; FR-045: no server-side draft state exists). The
 * composer writes one on every keystroke pause, so it survives closing the app, a
 * force-quit and a crash.
 *
 * Scoped to the person signed in: [read] never returns another person's draft, and
 * the whole store is wiped whenever a session ends or starts. Signed out, or still
 * deciding, there is nobody: [read] is null and [write] and [delete] do nothing.
 *
 * Nothing here may be logged: a draft is entry text.
 */
interface DraftStore {
    /** The signed-in person's draft of [kind] in [bondId], or null when there is none. */
    suspend fun read(bondId: String, kind: DraftKind): Draft?

    /** Keeps [draft], replacing the one of the same bond and kind. */
    suspend fun write(draft: Draft)

    /** Forgets the draft of [kind] in [bondId]: it was sent, saved, discarded or emptied. */
    suspend fun delete(bondId: String, kind: DraftKind)
}

/** Which composer a draft belongs to. The two are kept apart, so neither overwrites the other. */
enum class DraftKind {
    /** Today's entry, not yet sent. */
    NewEntry,

    /** Changes to my entry before the reveal, not yet saved. */
    Edit,
}

/**
 * One draft.
 *
 * @param entryId the entry an [DraftKind.Edit] draft changes; null for a new entry.
 * @param dayDate the Bond-day (ISO date) Today showed when the draft was last written.
 */
data class Draft(
    val bondId: String,
    val kind: DraftKind,
    val text: String,
    val dayDate: String,
    val entryId: String? = null,
) {
    /** Never the words: a data class would print them into any log that printed this. */
    override fun toString(): String =
        "Draft(bondId=$bondId, kind=$kind, dayDate=$dayDate, entryId=$entryId, text=(redacted))"
}
