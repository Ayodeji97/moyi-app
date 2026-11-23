package com.moyi.app

import com.moyi.app.domain.notification.PushKind
import com.moyi.app.domain.notification.SharedSettingChange
import kotlin.test.Test
import kotlin.test.assertEquals

class MoyiMessagingServiceTest {
    /** The resource name of a string id, read from the generated R class. */
    private fun nameOf(id: Int): String =
        R.string::class.java.fields.single { it.getInt(null) == id }.name

    @Test
    fun `a push in the foreground says what the tray would say in the background`() {
        // The tray resolves the server's loc keys by resource NAME; the foreground uses ids.
        PushKind.entries.forEach { kind ->
            val texts = MoyiMessagingService.textsOf(kind)
            assertEquals(kind.titleLocKey, nameOf(texts.title), kind.name)
            assertEquals(kind.bodyLocKey, nameOf(texts.body), kind.name)
        }
    }

    @Test
    fun `a shared setting change says the words the server picks for its change`() {
        SharedSettingChange.entries.forEach { change ->
            val texts = MoyiMessagingService.textsOf(PushKind.SharedSettingChanged, change)
            assertEquals(PushKind.SharedSettingChanged.titleLocKey, nameOf(texts.title), change.name)
            assertEquals(change.bodyLocKey, nameOf(texts.body), change.name)
        }
    }
}
