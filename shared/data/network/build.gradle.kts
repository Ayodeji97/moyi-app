plugins {
    id("moyi.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            api(project(":shared:data:api"))
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.ktor.client.logging)
            api(libs.ktor.client.auth)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// The API base URL is not decided here: a library has no debug or release variant. The app
// decides it per build type (androidApp BuildConfig, iOS Info.plist) and passes it to
// initKoin, which binds ApiConfig (CLAUDE.md, Configuration; README, "Which backend").
