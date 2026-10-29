import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

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
// `-PmoyiUiPreview=<screen>` starts the app on that screen's preview, running on
// fake state with no backend and no session, for checking a UI-only PR against
// Figma. Empty, the default, is the real app. It becomes the one generated
// string `UiPreviewConfig.UI_PREVIEW`; App() is the only reader.
//
// The allowed values are this one list, so a UI PR adds its screen in one line.
// Any other value fails the build.
val uiPreviewScreens = listOf("", "onboarding")

// Only the command line counts: the -P switches of THIS invocation. Never
// providers.gradleProperty, which also reads gradle.properties and
// ORG_GRADLE_PROJECT_*, and never local.properties: those persist, so a value
// left in one would ride along into the next build, release included.
val uiPreview: String = gradle.startParameter.projectProperties["moyiUiPreview"]?.trim().orEmpty()
require(uiPreview in uiPreviewScreens) {
    "moyiUiPreview='$uiPreview' is not one of ${uiPreviewScreens.filter { it.isNotEmpty() }}"
}

// The strict guard: a preview build is allowed only when it is plainly a debug
// build. Every requested task must name Debug, or the build must be Xcode's
// framework task running its Debug configuration. So `assemble`, `build`, a
// Release task and an Xcode Release build all fail, and so does anything on CI.
// It reads only the start parameters and providers, so the configuration
// cache stays valid.
if (uiPreview.isNotEmpty()) {
    val tasks = gradle.startParameter.taskNames
    val xcodeTasks = setOf("embedAndSignAppleFrameworkForXcode", ":shared:embedAndSignAppleFrameworkForXcode")
    val xcodeConfiguration = providers.environmentVariable("CONFIGURATION").orNull
    val ci = providers.environmentVariable("CI").orNull
    val allDebugTasks = tasks.isNotEmpty() && tasks.all { it.contains("Debug") }
    val xcodeDebug = tasks.isNotEmpty() && tasks.all { it in xcodeTasks } && xcodeConfiguration == "Debug"
    val reason = when {
        ci != null -> "CI is set"
        allDebugTasks || xcodeDebug -> null
        tasks.isNotEmpty() && tasks.all { it in xcodeTasks } ->
            "Xcode is building its '${xcodeConfiguration ?: "unset"}' configuration, not Debug"
        else -> "not every requested task is a Debug task (requested: ${tasks.ifEmpty { listOf("<default>") }})"
    }
    if (reason != null) {
        throw GradleException(
            "moyiUiPreview='$uiPreview' is a debug-only switch and $reason. " +
                "A preview build starts on fake data with no backend, so it must never ship. " +
                "Drop -PmoyiUiPreview for this build.",
        )
    }
}

buildkonfig {
    packageName = "com.moyi.app"
    objectName = "UiPreviewConfig"
    defaultConfigs {
        buildConfigField(STRING, "UI_PREVIEW", uiPreview)
    }
}
