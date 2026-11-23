package com.moyi.app.domain.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushPayloadTest {
    private val bondId = "3f2b8c1e-5a4d-4e6f-9b7a-1c2d3e4f5a6b"

    private fun push(kind: String?, bond: String? = bondId) = buildMap {
        if (kind != null) put(PushPayload.KEY_KIND, kind)
        if (bond != null) put(PushPayload.KEY_BOND_ID, bond)
    }

    @Test
    fun `every kind the server sends has its screen`() {
        val expected = mapOf(
            "REMINDER" to PushTarget.Today,
            "PARTNER_JOINED" to PushTarget.Today,
            "REVEAL" to PushTarget.Today,
            "SOLO_UNLOCK" to PushTarget.Today,
            "MILESTONE" to PushTarget.Streak,
            "ON_THIS_DAY" to PushTarget.Archive,
            "PROPOSAL_OPENED" to PushTarget.BondSettings(bondId),
            "SHARED_SETTING_CHANGED" to PushTarget.BondSettings(bondId),
        )
        // The same list as PushKind: a kind added to one and not here fails the next test too.
        assertEquals(PushKind.entries.map { it.wireName }.toSet(), expected.keys)
        expected.forEach { (kind, target) -> assertEquals(target, PushPayload.targetOf(push(kind)), kind) }
    }

    @Test
    fun `the kinds are the backend's PushCategory in its order`() {
        // moyi-backend modules/notification/.../api/Notifications.kt, read 2026-11-23 (main 7247ebe4).
        val backend = listOf(
            "REMINDER", "PARTNER_JOINED", "REVEAL", "SOLO_UNLOCK", "MILESTONE", "ON_THIS_DAY", "PROPOSAL_OPENED", "SHARED_SETTING_CHANGED",
        )
        assertEquals(backend, PushKind.entries.map { it.wireName })
    }

    @Test
    fun `channel ids and localisation keys follow the server's naming`() {
        assertEquals(
            listOf("reminder", "partner_joined", "reveal", "solo_unlock", "milestone", "on_this_day", "proposal_opened", "shared_setting_changed"),
            PushKind.entries.map { it.channelId },
        )
        assertEquals("push_reveal_title", PushKind.Reveal.titleLocKey)
        assertEquals("push_on_this_day_body", PushKind.OnThisDay.bodyLocKey)
    }

    @Test
    fun `an ordinary launch is no push`() {
        assertNull(PushPayload.targetOf(emptyMap()))
        assertNull(PushPayload.targetOf(mapOf("google.message_id" to "0:1")))
    }

    @Test
    fun `a kind this build does not know opens Today`() {
        assertEquals(PushTarget.Today, PushPayload.targetOf(push("SOMETHING_NEWER")))
        assertEquals(PushTarget.Today, PushPayload.targetOf(push("reveal")))
        assertEquals(PushTarget.Today, PushPayload.targetOf(push("")))
    }

    @Test
    fun `missing fields open Today`() {
        assertEquals(PushTarget.Today, PushPayload.targetOf(push(kind = null)))
        assertEquals(PushTarget.Today, PushPayload.targetOf(push("PROPOSAL_OPENED", bond = null)))
        assertEquals(PushTarget.Streak, PushPayload.targetOf(push("MILESTONE", bond = null)))
    }

    @Test
    fun `a bond id that is not a UUID never reaches a route`() {
        listOf("", "../me", "$bondId/leave", "not-a-uuid", "$bondId ").forEach { bad ->
            val target = PushPayload.targetOf(push("PROPOSAL_OPENED", bond = bad))
            val expected = if (bad.trim() == bondId) PushTarget.BondSettings(bondId) else PushTarget.Today
            assertEquals(expected, target, bad)
        }
    }

    @Test
    fun `surrounding whitespace in the kind is tolerated`() {
        assertEquals(PushTarget.Streak, PushPayload.targetOf(push(" MILESTONE ")))
    }

    private fun sharedChange(change: String?, bond: String? = bondId) =
        push("SHARED_SETTING_CHANGED", bond) + (change?.let { mapOf(PushPayload.KEY_CHANGE to it) } ?: emptyMap())

    @Test
    fun `a shared setting change opens that bond's settings whatever the change`() {
        listOf("SETTING", "SEVERAL", "PROPOSAL_ACCEPTED", "PROPOSAL_DECLINED", "PROPOSAL_WITHDRAWN").forEach { change ->
            assertEquals(PushTarget.BondSettings(bondId), PushPayload.targetOf(sharedChange(change)), change)
        }
    }

    @Test
    fun `an unknown or missing change still opens the bond's settings`() {
        assertEquals(PushTarget.BondSettings(bondId), PushPayload.targetOf(sharedChange("SOMETHING_NEWER")))
        assertEquals(PushTarget.BondSettings(bondId), PushPayload.targetOf(sharedChange("")))
        assertEquals(PushTarget.BondSettings(bondId), PushPayload.targetOf(sharedChange(null)))
    }

    @Test
    fun `a shared setting change with no usable bond id opens Today`() {
        assertEquals(PushTarget.Today, PushPayload.targetOf(sharedChange("SETTING", bond = null)))
        assertEquals(PushTarget.Today, PushPayload.targetOf(sharedChange("SETTING", bond = "../me")))
    }

    @Test
    fun `each change has the body key the server sends`() {
        // moyi-backend FcmPushSender.locKeys, read 2026-11-23 (main 7247ebe4).
        assertEquals(
            mapOf(
                "SETTING" to "push_shared_setting_changed_body",
                "SEVERAL" to "push_shared_setting_changed_body",
                "PROPOSAL_ACCEPTED" to "push_shared_setting_changed_accepted_body",
                "PROPOSAL_DECLINED" to "push_shared_setting_changed_declined_body",
                "PROPOSAL_WITHDRAWN" to "push_shared_setting_changed_withdrawn_body",
            ),
            SharedSettingChange.entries.associate { it.wireName to it.bodyLocKey },
        )
        assertEquals("push_shared_setting_changed_title", PushKind.SharedSettingChanged.titleLocKey)
        assertEquals(PushKind.SharedSettingChanged.bodyLocKey, SharedSettingChange.Setting.bodyLocKey)
    }

    @Test
    fun `an unknown or missing change reads as the generic one`() {
        assertEquals(SharedSettingChange.Setting, SharedSettingChange.fromWire("SOMETHING_NEWER"))
        assertEquals(SharedSettingChange.Setting, SharedSettingChange.fromWire(null))
        assertEquals(SharedSettingChange.Setting, SharedSettingChange.fromWire("proposal_declined"))
        assertEquals(SharedSettingChange.ProposalDeclined, SharedSettingChange.fromWire(" PROPOSAL_DECLINED "))
    }
}
