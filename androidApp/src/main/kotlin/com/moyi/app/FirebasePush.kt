package com.moyi.app

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Starts push for this launch, when this build has Firebase configured.
 *
 * Firebase starts itself (its `FirebaseInitProvider`) from the resources the
 * google-services plugin generates out of `google-services.json`. A build without that file
 * (CI, a fresh clone, release until moyi-prod exists) has no such resources: Firebase never
 * starts, [isConfigured] is false, nothing here runs, and the app works without push.
 */
internal object FirebasePush {
    private const val TAG = "Push"

    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Asks FCM for this device's current token and hands it to the shared code
     * ([pushTokenReceived]), which registers it with the server once someone is signed in.
     * `onNewToken` only fires when FCM issues a token, so without this a token issued in an
     * earlier launch would never reach a fresh process. The token itself is never logged.
     */
    fun start(context: Context) {
        if (!isConfigured(context)) {
            Log.i(TAG, "Firebase is not configured in this build: no push")
            return
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> pushTokenReceived(token) }
            .addOnFailureListener { error -> Log.w(TAG, "No FCM token yet: ${error::class.java.simpleName}") }
    }
}
