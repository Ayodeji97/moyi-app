import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        release {
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
    }
}