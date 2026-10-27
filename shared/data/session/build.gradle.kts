plugins {
    id("moyi.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

// The session: where the refresh token is kept (SecureTokenStore, Keychain on
// iOS, a Keystore-encrypted file on Android) and the one place that refreshes
// it (SessionManager). It implements network's BearerTokenSource, so the
// authed HttpClient can refresh without network depending on this module.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            api(project(":shared:domain"))
            api(project(":shared:data:api"))
            api(project(":shared:data:network"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            // DarwinHttpRequestException, to classify a failed refresh (RefreshFailureKind).
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(project(":shared:data:testing"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
