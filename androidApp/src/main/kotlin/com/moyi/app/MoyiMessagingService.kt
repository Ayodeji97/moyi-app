package com.moyi.app

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.moyi.app.domain.notification.PushKind
import com.moyi.app.domain.notification.PushPayload

/**
 * FCM's entry into the app (the manifest's `com.google.firebase.MESSAGING_EVENT` service).
 *
 * **[onNewToken]**: FCM issued or rotated this device's token. It goes to the shared code
 * ([pushTokenReceived]), which tells the server while someone is signed in
 * (`PushTokenRegistrar`). Never logged: the token is a secret device identifier.
 *
 * **[onMessageReceived]**: a push arrived **while the app is in the foreground**. The
 * server sends notification messages (`FcmPushSender`: a title and body loc key, and
 * `data: {bondId, kind}`); in the background the system tray shows those by itself and
 * this is not called. In the foreground FCM shows nothing, so this posts the same
 * notification: in the kind's channel ([PushKind.channelId], or the reveal's for a kind
 * this build does not know), with the kind's strings, and a tap that opens [MainActivity]
 * with `bondId` and `kind` as extras, exactly as a tray notification's tap does
 * (`MainActivity.handOver`, `PushTapInbox`). Nothing of the push is logged.
 */
class MoyiMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        pushTokenReceived(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val bondId = message.data[PushPayload.KEY_BOND_ID]
        val rawKind = message.data[PushPayload.KEY_KIND]
        val manager = NotificationManagerCompat.from(this)
        // Android 13+ asks for the permission at runtime (Bond settings, Notifications);
        // without it, or with Moyi's notifications turned off, nothing may be posted.
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permitted || !manager.areNotificationsEnabled()) return

        val kind = PushKind.fromWire(rawKind)
        val texts = kind?.let(::textsOf)
        val tap = Intent(this, MainActivity::class.java).apply {
            // singleTop in the manifest: a running activity gets onNewIntent and keeps its state.
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(PushPayload.KEY_BOND_ID, bondId)
            putExtra(PushPayload.KEY_KIND, rawKind)
        }
        // One notification per kind and bond: a newer push of the same kind replaces the
        // older one instead of stacking up.
        val id = "$rawKind/$bondId".hashCode()
        val pending = PendingIntent.getActivity(
            this,
            id,
            tap,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, kind?.channelId ?: NotificationChannels.DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(texts?.title ?: R.string.app_name))
            .apply { texts?.body?.let { setContentText(getString(it)) } }
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    internal data class Texts(@param:StringRes val title: Int, @param:StringRes val body: Int)

    internal companion object {
        /**
         * The strings the tray shows for [kind] in the background: the resources named by the
         * server's loc keys ([PushKind.titleLocKey], [PushKind.bodyLocKey]), so the foreground
         * says the same words. A `when`, so a new kind does not compile until it has its text.
         */
        fun textsOf(kind: PushKind): Texts = when (kind) {
            PushKind.Reminder -> Texts(R.string.push_reminder_title, R.string.push_reminder_body)
            PushKind.PartnerJoined -> Texts(R.string.push_partner_joined_title, R.string.push_partner_joined_body)
            PushKind.Reveal -> Texts(R.string.push_reveal_title, R.string.push_reveal_body)
            PushKind.SoloUnlock -> Texts(R.string.push_solo_unlock_title, R.string.push_solo_unlock_body)
            PushKind.Milestone -> Texts(R.string.push_milestone_title, R.string.push_milestone_body)
            PushKind.OnThisDay -> Texts(R.string.push_on_this_day_title, R.string.push_on_this_day_body)
            PushKind.ProposalOpened -> Texts(R.string.push_proposal_opened_title, R.string.push_proposal_opened_body)
        }
    }
}
