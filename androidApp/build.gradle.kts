import com.android.build.api.variant.HostTestBuilder
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import com.moyi.buildlogic.ANDROID_DEBUG_API_BASE_URL
import com.moyi.buildlogic.DebugNetworkSecurityConfigTask
import com.moyi.buildlogic.PRODUCTION_API_BASE_URL
import com.moyi.buildlogic.checkedDevelopmentBaseUrl
import com.moyi.buildlogic.moyiLocalProperty
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // Push (FCM): turns google-services.json into the resources Firebase starts from.
    alias(libs.plugins.googleServices)
}

// Firebase's config is per build type and never committed (README, "Push notifications"):
// src/debug/google-services.json (the dev project, moyi-65b68) and
// src/release/google-services.json (moyi-prod). The plugin looks in src/<buildType>/ first,
// then this directory. Where a variant has no file (CI, a fresh clone, release until the
// prod project exists) the plugin warns instead of failing: no Firebase resources are
// generated, Firebase never starts (FirebasePush), and the app runs without push.
googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

// A release build never talks to the development Firebase project, the same way a
// development build never talks to production (CLAUDE.md). A dev file left in this
// directory (instead of src/debug/) would otherwise be picked up by release too.
tasks.matching { it.name == "processReleaseGoogleServices" }.configureEach {
    // Locals only: the action is stored in the configuration cache, which cannot hold the
    // build script itself.
    val devProject = "moyi-65b68"
    val releaseConfigs = listOf(file("src/release/google-services.json"), file("google-services.json"))
    doFirst {
        val config = releaseConfigs.firstOrNull { it.isFile } ?: return@doFirst
        if (Regex(""""project_id"\s*:\s*"$devProject"""").containsMatchIn(config.readText())) {
            throw GradleException(
                "The release build would use the development Firebase project ($devProject), from " +
                    "${config.path}. Move that file to src/debug/ and put the moyi-prod one in src/release/.",
            )
        }
    }
}
// ---- API base URL (CLAUDE.md, Configuration; README, "Which backend the app talks to") ----
//
// Decided here, per build type, and handed to initKoin as BuildConfig.API_BASE_URL:
//  - debug: the LOCAL backend. The emulator reaches the Mac as 10.0.2.2; `moyi.apiBaseUrl`
//    (-P or local.properties) overrides it. A production host fails the build, here at
//    configuration time, before anything is compiled.
//  - release: production, always. The override does not reach release.
// CI checks both values (ApiBaseUrlTest, run for both build types by `check`).
val apiBaseUrlOverride: String? = moyiLocalProperty("moyi.apiBaseUrl").orNull
val overrideSource = if (providers.gradleProperty("moyi.apiBaseUrl").isPresent) "-Pmoyi.apiBaseUrl" else "local.properties"
val debugApiBaseUrl: String = checkedDevelopmentBaseUrl(
    apiBaseUrlOverride ?: ANDROID_DEBUG_API_BASE_URL,
    if (apiBaseUrlOverride != null) overrideSource else "the Android default",
)

// ---- Debug cleartext allowlist (README, "Which backend the app talks to") ----------------
//
// The local backend speaks plain HTTP, and so does the MinIO it signs photo and voice URLs
// for (http://<the Mac's LAN address>:9000). Debug builds allow cleartext to:
//  - 10.0.2.2, localhost and 127.0.0.1: the Mac from the emulator, and `adb reverse`;
//  - the host of moyi.apiBaseUrl, so a phone on the same Wi-Fi reaches the Mac;
//  - moyi.devMediaHosts (comma-separated), for a media host the base URL does not name,
//    e.g. the emulator on 10.0.2.2 with MinIO signing for 192.168.1.70.
// The file is generated (DebugNetworkSecurityConfigTask) because these hosts are each
// developer's own. A production host fails the build. Release has no such file and keeps
// Android's default: HTTPS only.
val debugCleartextHosts: Provider<List<String>> = moyiLocalProperty("moyi.apiBaseUrl")
    .map { url -> URI(url).host.orEmpty() }
    .orElse("")
    .zip(moyiLocalProperty("moyi.devMediaHosts").orElse("")) { apiHost: String, media: String ->
        listOf("10.0.2.2", "localhost", "127.0.0.1", apiHost) + media.split(',')
    }
val generateDebugNetworkSecurityConfig =
    tasks.register<DebugNetworkSecurityConfigTask>("generateDebugNetworkSecurityConfig") {
        hosts.set(debugCleartextHosts)
        outputDir.set(layout.buildDirectory.dir("generated/res/debugNetworkSecurity"))
    }
androidComponents {
    // AGP 9 runs unit tests for the debug build type only. ApiBaseUrlTest must also see the
    // release BuildConfig, so `check` (CI) proves release carries exactly production.
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            generateDebugNetworkSecurityConfig,
            DebugNetworkSecurityConfigTask::outputDir,
        )
    }
}

dependencies {
    implementation(project(":shared"))
    // PushKind and PushPayload: the channel ids and the keys a tapped push carries.
    implementation(project(":shared:domain"))

    implementation(libs.androidx.activity.compose)
    // NotificationCompat, for a push that arrives while the app is open (MoyiMessagingService).
    implementation(libs.androidx.core.ktx)
    // FCM: the token (MoyiMessagingService.onNewToken) and foreground pushes.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // androidContext() for initKoin in MoyiApplication.
    implementation(libs.koin.android)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
}

android {
    namespace = "com.moyi.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // The store-facing ID is getmoyi.app reversed, the same on both platforms (issue #49).
        // The Kotlin package and namespace stay com.moyi.app.
        applicationId = "app.getmoyi.moyi"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // The upload key lives only on the owner's Mac (~/.gradle/gradle.properties, issue #49).
    // Where the four properties are missing (CI, any other machine) the release build is
    // left unsigned instead of failing; Google Play re-signs uploads with its own app key.
    val uploadStoreFile = providers.gradleProperty("MOYI_UPLOAD_STORE_FILE").orNull
    val uploadSigning = uploadStoreFile?.let {
        signingConfigs.create("upload") {
            storeFile = file(it)
            storePassword = providers.gradleProperty("MOYI_UPLOAD_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("MOYI_UPLOAD_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("MOYI_UPLOAD_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$PRODUCTION_API_BASE_URL\"")
            signingConfig = uploadSigning
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.API_BASE_URL, the one value that differs between debug and release.
        buildConfig = true
    }
}