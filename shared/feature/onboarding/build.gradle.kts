plugins {
    id("moyi.kmp.compose")
    // Navigation routes are @Serializable classes.
    alias(libs.plugins.kotlinSerialization)
}

// Onboarding: the signed-in start, the unverified gate, the promise,
// relationship type, create bond, the invite handoff, and the joiner's code,
// confirm and cannot-be-used screens, over the domain's bond and account
// repositories. Like auth, it may see the domain, never the data layer (the
// Konsist rule covers every module under shared/feature).
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
            implementation(project(":shared:designsystem"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // The device's IANA time zone, the default anchor zone (states.md §1).
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moyi.app.feature.onboarding.resources"
}
