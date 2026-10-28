rootProject.name = "Moyi"

pluginManagement {
    // Convention plugins: moyi.kmp.library, moyi.kmp.compose, moyi.dependency.guard.
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")

// The umbrella: App(), and the one iOS framework ("Shared") that Xcode links.
include(":shared")

include(":shared:core")
include(":shared:domain")
include(":shared:designsystem")
include(":shared:data:api")
include(":shared:data:network")
include(":shared:data:session")
include(":shared:data:repository")
include(":shared:data:testing")
include(":shared:feature:auth")
include(":shared:feature:onboarding")
include(":shared:feature:today")

// JVM-only Konsist rules over the shared sources.
include(":architecture-tests")
