package com.moyi.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.moyi.app.domain.notification.PushKind

/**
 * Android's notification channels, one per kind of push, so a person can silence one kind
 * in the system settings and keep the others (doc 13 §7). Android 8 (API 26) and later show
 * every notification in a channel. Below 8 (minSdk is 24) there are no channels: a push is
 * shown with the importance FCM gives it, and the person can only turn Moyi's off as a whole.
 *
 * **The ids are [PushKind.channelId]**, the server's category in lower case, which is what
 * backend issue #134 asks the server to send as `android.notification.channel_id`. Until
 * it does, FCM shows every push in the manifest's `default_notification_channel_id`
 * ([DEFAULT_CHANNEL_ID]).
 *
 * **Importance is the loudest a channel may be**, and the person can only lower it. None is
 * HIGH (a heads-up banner over whatever is on screen): Moyi has no urgent news (copy.md, no
 * artificial urgency). DEFAULT makes a sound and shows in the status bar; LOW is silent.
 */
internal object NotificationChannels {
    data class Spec(
        val kind: PushKind,
        @param:StringRes val name: Int,
        @param:StringRes val description: Int,
        val importance: Int,
    ) {
        val id: String get() = kind.channelId
    }

    val specs: List<Spec> = listOf(
        // An invitation the person asked for, at a time they chose; a silent one is easily missed.
        Spec(PushKind.Reminder, R.string.channel_reminder_name, R.string.channel_reminder_description, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(PushKind.PartnerJoined, R.string.channel_partner_joined_name, R.string.channel_partner_joined_description, NotificationManager.IMPORTANCE_DEFAULT),
        // The one push that is genuinely news (copy.md), and the reserved slot (FR-082).
        Spec(PushKind.Reveal, R.string.channel_reveal_name, R.string.channel_reveal_description, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(PushKind.SoloUnlock, R.string.channel_solo_unlock_name, R.string.channel_solo_unlock_description, NotificationManager.IMPORTANCE_DEFAULT),
        // "Restrained" (copy.md): shown, never sounded.
        Spec(PushKind.Milestone, R.string.channel_milestone_name, R.string.channel_milestone_description, NotificationManager.IMPORTANCE_LOW),
        // "Gentle, easy to turn off" (copy.md): shown, never sounded.
        Spec(PushKind.OnThisDay, R.string.channel_on_this_day_name, R.string.channel_on_this_day_description, NotificationManager.IMPORTANCE_LOW),
        // Waits on this person's answer, so it is heard like the others that matter.
        Spec(PushKind.ProposalOpened, R.string.channel_proposal_opened_name, R.string.channel_proposal_opened_description, NotificationManager.IMPORTANCE_DEFAULT),
    )

    /**
     * Where FCM puts a push that names no channel (every push, until backend #134): the
     * reveal's. It is the push that matters most, its importance suits any kind, and its
     * name is the truest for most of what arrives. Must equal the manifest's
     * `com.google.firebase.messaging.default_notification_channel_id` (a test reads both).
     */
    val DEFAULT_CHANNEL_ID: String = PushKind.Reveal.channelId

    /**
     * Creates every channel. Safe on every start: creating a channel that exists only
     * updates its name and description, never the importance or sound the person chose.
     */
    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            specs.map { spec ->
                NotificationChannel(spec.id, context.getString(spec.name), spec.importance).apply {
                    description = context.getString(spec.description)
                }
            },
        )
    }
}
