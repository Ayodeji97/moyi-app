plugins {
    id("moyi.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            // System back on screens with no back arrow (BackHandlers.kt, D10, D12).
            implementation(libs.androidx.navigationevent.compose)
            // The photo on an EntryCard and in the composer (doc 13 §3: Coil 3.x).
            implementation(libs.coil.compose)
            // A voice note's player pauses when the app stops (LifecycleEventEffect).
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        // Chrome Custom Tabs for the in-app browser (InAppBrowser, D8).
        androidMain.dependencies {
            implementation(libs.androidx.browser)
            // The POST_NOTIFICATIONS request needs an ActivityResult launcher (NotificationPermission.android.kt).
            implementation(libs.androidx.activity.compose)
            // The camera writes its photo to a FileProvider URI (rememberPhotoPicker).
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.exifinterface)
            // Voice notes: Ogg Opus from a signed URL, streamed without a cache (rememberVoicePlayer).
            implementation(libs.androidx.media3.exoplayer)
        }
        // The voice player's state and ticker are tested under virtual time (VoicePlaybackTest).
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        // Compose UI tests run on iOS (Skiko, no device needed beyond the test
        // runner); on the Android host they would need Robolectric.
        // The iOS recorder logs which audio call failed, with its error code (VoiceRecorder.ios.kt).
        iosMain.dependencies {
            implementation(libs.kermit)
        }
        iosTest.dependencies {
            implementation(libs.compose.uiTest)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moyi.app.designsystem.resources"
}
