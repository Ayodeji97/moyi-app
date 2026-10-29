import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import com.moyi.buildlogic.moyiLocalProperty
import java.net.URI

plugins {
    id("moyi.kmp.compose")
    // Navigation routes are @Serializable classes.
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildkonfig)
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

// ---- Legal page URLs (figma-deviations.md D8) ---------------------------------
//
// The Terms and the Privacy Policy open in the in-app browser. Their URLs are
// configuration, not code: no published page exists yet (owed with the site's
// legal pages, or with `GET /meta/legal`, doc 06 §3.7, which the contract does
// not have yet). Set `moyi.termsUrl` and `moyi.privacyUrl` with -P or in the
// root local.properties. Unset, the links do nothing, as before. A value that
// is not an https URL with a host fails the build.

fun legalUrl(name: String): String {
    val value = moyiLocalProperty(name).orNull ?: return ""
    val uri = runCatching { URI(value) }.getOrNull()
    require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank()) {
        "$name must be an https URL with a host, got '$value'"
    }
    return value
}

buildkonfig {
    packageName = "com.moyi.app.feature.auth"
    objectName = "LegalConfig"
    defaultConfigs {
        buildConfigField(STRING, "TERMS_URL", legalUrl("moyi.termsUrl"))
        buildConfigField(STRING, "PRIVACY_URL", legalUrl("moyi.privacyUrl"))
    }
}
