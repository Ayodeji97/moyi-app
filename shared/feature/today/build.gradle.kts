plugins {
    id("moyi.kmp.compose")
    // Navigation routes are @Serializable classes.
    alias(libs.plugins.kotlinSerialization)
}

// The daily loop: Today, Write, Reveal, and delete-after-reveal. UI ONLY for now
// (PR #8): every screen renders FAKE state from FakeDailyLoop, and each place a
// real call or rule belongs carries a `TODO(logic)` naming the contract endpoint.
// Like every feature it may see the domain, never the data layer (Konsist).
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:designsystem"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        // TodayBackTest drives a real NavHost and sends system back through the
        // navigation event dispatcher. Compose UI tests run on iOS only (Skiko);
        // on the Android host they would need Robolectric.
        iosTest.dependencies {
            implementation(libs.compose.uiTest)
            implementation(libs.androidx.navigationevent.compose)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moyi.app.feature.today.resources"
}
