package com.moyi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.moyi.app.domain.notification.PushPayload
import org.koin.android.ext.android.inject

/**
 * The one activity. Besides hosting [App], it hands a tapped push to the shell.
 *
 * When a push's notification is tapped, the system starts this activity with the push's
 * `data` as intent extras (`bondId`, `kind`, beside FCM's own `google.*` keys):
 * - **cold start** (no process, or no activity): the intent arrives in [onCreate];
 * - **warm start** (the activity exists, in the background): with `singleTop` in the
 *   manifest it arrives in [onNewIntent], and the running screen keeps its state.
 */
class MainActivity : ComponentActivity() {
    private val pushTaps: PushTapInbox by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only a fresh launch carries a tap. A recreation (rotation, process restored)
        // replays the launch intent, which was already handled the first time.
        if (savedInstanceState == null) handOver(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handOver(intent)
    }

    /**
     * Takes the two keys the app reads, and only those, out of [intent] (nothing of it is
     * logged), then removes them so the same tap is never handed over twice.
     */
    private fun handOver(intent: Intent) {
        // Reopened from Recents: the original intent again, not a new tap.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val extras = intent.extras ?: return
        pushTaps.tapped(
            mapOf(
                PushPayload.KEY_BOND_ID to extras.getString(PushPayload.KEY_BOND_ID),
                PushPayload.KEY_KIND to extras.getString(PushPayload.KEY_KIND),
            ),
        )
        intent.removeExtra(PushPayload.KEY_BOND_ID)
        intent.removeExtra(PushPayload.KEY_KIND)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
