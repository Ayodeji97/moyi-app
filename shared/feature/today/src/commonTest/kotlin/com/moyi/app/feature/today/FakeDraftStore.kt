package com.moyi.app.feature.today

import com.moyi.app.domain.today.Draft
import com.moyi.app.domain.today.DraftKind
import com.moyi.app.domain.today.DraftStore

/** A [DraftStore] in a map, for the composer's tests: one draft per bond and kind, every write counted. */
class FakeDraftStore : DraftStore {
    val drafts = mutableMapOf<Pair<String, DraftKind>, Draft>()
    var writes = 0

    /** Set to make every call throw, as a broken database would. */
    var broken = false

    override suspend fun read(bondId: String, kind: DraftKind): Draft? {
        check(!broken) { "broken" }
        return drafts[bondId to kind]
    }

    override suspend fun write(draft: Draft) {
        check(!broken) { "broken" }
        writes++
        drafts[draft.bondId to draft.kind] = draft
    }

    override suspend fun delete(bondId: String, kind: DraftKind) {
        check(!broken) { "broken" }
        drafts.remove(bondId to kind)
    }

    operator fun get(kind: DraftKind): Draft? = drafts["bond-1" to kind]
}
