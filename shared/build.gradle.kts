import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN

plugins {
    id("moyi.kmp.compose")
    // Navigation routes (the debug preview index) are @Serializable classes.
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildkonfig)
}

// The umbrella module. It depends on every other shared module, holds App(),
// and is the single iOS framework ("Shared") the Xcode project links: iOS can
// embed only one Kotlin framework, so everything is reached through this one.
// Nothing is `export()`ed: Swift calls only MainViewController().
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Without it the compiler guesses and warns on every link.
            binaryOption("bundleId", "com.moyi.app.shared")
        }
    }

    android {
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
            implementation(project(":shared:designsystem"))
            implementation(project(":shared:data:api"))
            implementation(project(":shared:data:network"))
            implementation(project(":shared:data:session"))
            implementation(project(":shared:data:repository"))
            implementation(project(":shared:feature:auth"))
            implementation(project(":shared:feature:onboarding"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
            // api: initKoin's signature (KoinAppDeclaration) is called from androidApp.
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
    }
}

// ---- UI preview (DEBUG ONLY; README "UI preview") -----------------------------
//
// `-PmoyiUiPreview=true` (or ORG_GRADLE_PROJECT_moyiUiPreview=true for an
// Xcode build) starts the app at a preview index of the onboarding screens,
// which run on fake state and need no backend or session. Off by default, so
// a build nobody asked to change never shows it. A release task with it on
// fails here, before anything is built.
val uiPreview: Boolean = providers.gradleProperty("moyiUiPreview").map { it.toBoolean() }.getOrElse(false)
if (uiPreview && gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    throw GradleException("-PmoyiUiPreview=true is for debug builds only; it was set on a release task.")
}

buildkonfig {
    packageName = "com.moyi.app"
    objectName = "AppBuildKonfig"
    defaultConfigs {
        buildConfigField(BOOLEAN, "UI_PREVIEW", uiPreview.toString())
    }
}
