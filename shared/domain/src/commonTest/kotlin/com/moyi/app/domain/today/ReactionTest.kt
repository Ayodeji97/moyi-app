package com.moyi.app.domain.today

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Who may react (ADR-0037 decisions 1 to 5): only to the partner's revealed entry. */
class ReactionTest {
    @Test
    fun onlyThePartnersRevealedEntryTakesMyReaction() {
        val revealed = PartnerEntry.Revealed("e2", "You listened.", reaction = Reaction.HEART)
        assertEquals(revealed, revealed.reactable())
    }

    @Test
    fun noOtherShapeTakesOne() {
        // Not yet revealed (locked), its tombstone, a solo day's unlocked entry, and no entry at all.
        assertNull(PartnerEntry.Locked.reactable())
        assertNull(PartnerEntry.Deleted("e2").reactable())
        assertNull(PartnerEntry.Submitted("e2", "You listened.").reactable())
        assertNull((null as PartnerEntry?).reactable())
    }

    @Test
    fun theSetIsTheOwnersFive() {
        assertEquals(listOf("HEART", "THANK_YOU", "SMILE", "MOVED", "ME_TOO"), Reaction.entries.map { it.name })
    }

    @Test
    fun aReactionIsSafeToLogButTheWordsAreNot() {
        val entry = MyEntry("e1", "You made the tea.", EntryStatus.REVEALED, reaction = Reaction.MOVED)
        assertEquals(false, entry.toString().contains("tea"))
        assertEquals(true, entry.toString().contains("reaction=MOVED"))
    }
}
