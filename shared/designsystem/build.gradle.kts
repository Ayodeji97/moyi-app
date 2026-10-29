plugins {
    id("moyi.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
        }
        // Chrome Custom Tabs for the in-app browser (InAppBrowser, D8).
        androidMain.dependencies {
            implementation(libs.androidx.browser)
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
