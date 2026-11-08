package com.moyi.app

import com.moyi.app.domain.notification.PushKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationChannelsTest {
    @Test
    fun `one channel per kind of push, with the ids the server is asked to send`() {
        // backend issue #134: android.notification.channel_id = the category in lower case.
        assertEquals(
            listOf("reminder", "partner_joined", "reveal", "solo_unlock", "milestone", "on_this_day", "proposal_opened"),
            NotificationChannels.specs.map { it.id },
        )
        assertEquals(PushKind.entries, NotificationChannels.specs.map { it.kind })
    }

    @Test
    fun `no channel interrupts with a heads-up banner`() {
        // NotificationManager.IMPORTANCE_HIGH is 4; constants are inlined, so the literal is the value.
        NotificationChannels.specs.forEach { assertTrue(it.importance < 4, it.id) }
    }

    @Test
    fun `the manifest's default channel is one the app creates`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val value = Regex(
            """default_notification_channel_id"\s+android:value="([^"]+)"""",
        ).find(manifest)?.groupValues?.get(1)
        assertEquals(NotificationChannels.DEFAULT_CHANNEL_ID, value)
        assertTrue(NotificationChannels.specs.any { it.id == value })
    }

    @Test
    fun `every key the server sends has its string on both platforms`() {
        val android = File("src/main/res/values/push_strings.xml").readText()
        val ios = File("../iosApp/iosApp/en.lproj/Localizable.strings").readText()
        PushKind.entries.flatMap { listOf(it.titleLocKey, it.bodyLocKey) }.forEach { key ->
            assertTrue("<string name=\"$key\">" in android, "Android lacks $key")
            assertTrue("\"$key\" =" in ios, "iOS lacks $key")
        }
    }
}
