import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import com.moyi.buildlogic.moyiLocalProperty
import java.net.URI

plugins {
    id("moyi.kmp.library")
    alias(libs.plugins.buildkonfig)
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

// ---- API base URL (CLAUDE.md, Configuration) --------------------------------
//
// Defaults point at the LOCAL backend: the Android emulator reaches the Mac as
// 10.0.2.2, the iOS simulator shares the Mac's localhost. `moyi.apiBaseUrl`
// overrides both, from -P or the root local.properties (moyiLocalProperty, in
// build-logic, reads it through providers.fileContents so the configuration
// cache tracks the file and a changed value is never silently ignored).

val androidDefaultBaseUrl = "http://10.0.2.2:8080/api/v1"
val iosDefaultBaseUrl = "http://localhost:8080/api/v1"

val apiBaseUrlOverride: String? = moyiLocalProperty("moyi.apiBaseUrl").orNull

/**
 * Fails the build for a base URL that is malformed or points at production.
 * Real people's data lives on getmoyi.app; a development build that talks to
 * it can write into a real account. There is no release flavor yet, so the
 * guard holds for every build; store builds will opt out explicitly.
 */
fun checkedBaseUrl(url: String, source: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val host = uri?.host?.lowercase()
    require(uri != null && uri.scheme in setOf("http", "https") && host != null) {
        "moyi.apiBaseUrl from $source is not an http(s) URL with a host: '$url'"
    }
    if (host == "getmoyi.app" || host.endsWith(".getmoyi.app")) {
        throw GradleException(
            "moyi.apiBaseUrl from $source points at production ($host).\n" +
                "Development builds never talk to getmoyi.app: real people's data lives there " +
                "(CLAUDE.md, Configuration). Point it at a local backend, e.g. http://localhost:8080/api/v1, " +
                "or remove the override.",
        )
    }
    require(uri.path.trimEnd('/').endsWith("/api/v1")) {
        "moyi.apiBaseUrl from $source must end in /api/v1, the same form as Bruno and doc 06: '$url'"
    }
    return url
}

val overrideSource = if (providers.gradleProperty("moyi.apiBaseUrl").isPresent) "-Pmoyi.apiBaseUrl" else "local.properties"
val androidBaseUrl = checkedBaseUrl(apiBaseUrlOverride ?: androidDefaultBaseUrl, apiBaseUrlOverride?.let { overrideSource } ?: "the Android default")
val iosBaseUrl = checkedBaseUrl(apiBaseUrlOverride ?: iosDefaultBaseUrl, apiBaseUrlOverride?.let { overrideSource } ?: "the iOS default")

buildkonfig {
    packageName = "com.moyi.app.data.network"

    defaultConfigs {
        buildConfigField(STRING, "API_BASE_URL", androidBaseUrl)
    }
    targetConfigs {
        create("android") {
            buildConfigField(STRING, "API_BASE_URL", androidBaseUrl)
        }
        create("iosArm64") {
            buildConfigField(STRING, "API_BASE_URL", iosBaseUrl)
        }
        create("iosSimulatorArm64") {
            buildConfigField(STRING, "API_BASE_URL", iosBaseUrl)
        }
    }
}
