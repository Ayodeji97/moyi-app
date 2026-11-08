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
        }
        // Chrome Custom Tabs for the in-app browser (InAppBrowser, D8).
        androidMain.dependencies {
            implementation(libs.androidx.browser)
            // The POST_NOTIFICATIONS request needs an ActivityResult launcher (NotificationPermission.android.kt).
            implementation(libs.androidx.activity.compose)
            // The camera writes its photo to a FileProvider URI (rememberPhotoPicker).
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.exifinterface)
        }
        // Compose UI tests run on iOS (Skiko, no device needed beyond the test
        // runner); on the Android host they would need Robolectric.
        iosTest.dependencies {
            implementation(libs.compose.uiTest)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moyi.app.designsystem.resources"
}
