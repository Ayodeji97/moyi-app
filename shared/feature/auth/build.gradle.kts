plugins {
    id("moyi.kmp.compose")
    // Navigation routes are @Serializable classes.
    alias(libs.plugins.kotlinSerialization)
}

// The auth flow: register, verify email, sign in, and the temporary signed-in
// placeholder. It sees the domain's AuthRepository, never the data layer
// (a Konsist rule fails the build if a feature imports com.moyi.app.data).
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
    packageOfResClass = "com.moyi.app.feature.auth.resources"
}
