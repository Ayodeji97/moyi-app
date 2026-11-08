plugins {
    id("moyi.kmp.compose")
    // Navigation routes are @Serializable classes.
    alias(libs.plugins.kotlinSerialization)
}

// The daily loop: Today, Write, Reveal, and delete-after-reveal, over the domain's
// BondRepository and TodayRepository (through DailyLoop). Like every feature it
// may see the domain, never the data layer or the generated client (Konsist).
// kotlinx-datetime parses the Bond-day's ISO date for the locale date label.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
            implementation(project(":shared:designsystem"))
            implementation(libs.kotlinx.datetime)
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
