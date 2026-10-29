plugins {
    id("moyi.kmp.compose")
    // Navigation routes are @Serializable classes.
    alias(libs.plugins.kotlinSerialization)
}

// Onboarding: the promise, relationship type, create bond, the invite handoff,
// and the joiner's code, confirm and cannot-be-used screens. PR #7 builds the
// UI only: every ViewModel holds fake state, and each real call is a
// `TODO(logic)` naming its endpoint. Like auth, it may see the domain, never
// the data layer (the Konsist rule covers every module under shared/feature).
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
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
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moyi.app.feature.onboarding.resources"
}
