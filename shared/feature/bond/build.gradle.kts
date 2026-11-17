plugins {
    id("moyi.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
            implementation(project(":shared:designsystem"))
            // The relationship-type cards and their labels: states.md §1a keeps the card in
            // onboarding until a third use appears, and Bond settings is the second.
            implementation(project(":shared:feature:onboarding"))
            // The platform's list of zone ids for the time-zone picker, and the invite's expiry date.
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
        // Compose UI tests run on iOS (Skiko); on the Android host they would need Robolectric.
        iosTest.dependencies {
            implementation(libs.compose.uiTest)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moyi.app.feature.bond.resources"
}
