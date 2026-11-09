import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))
    // PushKind and PushPayload: the channel ids and the keys a tapped push carries.
    implementation(project(":shared:domain"))

    implementation(libs.androidx.activity.compose)
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