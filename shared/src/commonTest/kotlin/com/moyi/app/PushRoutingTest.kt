package com.moyi.app

import com.moyi.app.domain.notification.PushPayload
import com.moyi.app.domain.notification.PushTarget
import com.moyi.app.feature.bond.navigation.BondSettingsGraph
import com.moyi.app.feature.today.navigation.ArchiveRoute
import com.moyi.app.feature.today.navigation.StreakRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushRoutingTest {
    private val bondId = "3f2b8c1e-5a4d-4e6f-9b7a-1c2d3e4f5a6b"
    private val targets = listOf(PushTarget.Today, PushTarget.Archive, PushTarget.Streak, PushTarget.BondSettings(bondId))

    @Test
    fun `a tap waits while the shell is settling whatever it is for`() {
        targets.forEach { assertEquals(PushRouting.Wait, pushRouting(it, ShellPlace.Settling), it.toString()) }
    }

    @Test
    fun `a tap is dropped when the signed-in start lands outside the app`() {
        targets.forEach { assertEquals(PushRouting.Drop, pushRouting(it, ShellPlace.OutsideTheApp), it.toString()) }
    }

    @Test
    fun `in the signed-in app each target stacks its screens above Today`() {
        assertEquals(PushRouting.Open(emptyList()), pushRouting(PushTarget.Today, ShellPlace.SignedInApp))
        assertEquals(PushRouting.Open(listOf(ArchiveRoute)), pushRouting(PushTarget.Archive, ShellPlace.SignedInApp))
        // Streak's back returns to the Archive, as when reached by hand.
        assertEquals(PushRouting.Open(listOf(ArchiveRoute, StreakRoute)), pushRouting(PushTarget.Streak, ShellPlace.SignedInApp))
        assertEquals(
            PushRouting.Open(listOf(BondSettingsGraph(bondId))),
            pushRouting(PushTarget.BondSettings(bondId), ShellPlace.SignedInApp),
        )
    }

    @Test
    fun `the inbox keeps the latest tap and ignores an ordinary launch`() {
        val inbox = PushTapInbox()
        inbox.tapped(emptyMap())
        assertNull(inbox.target.value)

        inbox.tapped(mapOf(PushPayload.KEY_KIND to "MILESTONE", PushPayload.KEY_BOND_ID to bondId))
        inbox.tapped(mapOf(PushPayload.KEY_KIND to "PROPOSAL_OPENED", PushPayload.KEY_BOND_ID to bondId))
        assertEquals(PushTarget.BondSettings(bondId), inbox.target.value)

        inbox.tapped(emptyMap())
        assertEquals(PushTarget.BondSettings(bondId), inbox.target.value)
    }

    @Test
    fun `consuming an older tap keeps a newer one`() {
        val inbox = PushTapInbox()
        inbox.tapped(mapOf(PushPayload.KEY_KIND to "REVEAL", PushPayload.KEY_BOND_ID to bondId))
        val routed = inbox.target.value!!
        inbox.tapped(mapOf(PushPayload.KEY_KIND to "MILESTONE", PushPayload.KEY_BOND_ID to bondId))
        inbox.consume(routed)
        assertEquals(PushTarget.Streak, inbox.target.value)
        inbox.consume(PushTarget.Streak)
        assertNull(inbox.target.value)
    }
}
