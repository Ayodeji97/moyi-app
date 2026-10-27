plugins {
    id("moyi.kmp.compose")
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
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
    }
}
