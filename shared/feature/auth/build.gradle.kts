plugins {
    id("moyi.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
            implementation(project(":shared:designsystem"))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moyi.app.feature.auth.resources"
}
