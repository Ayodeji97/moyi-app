plugins {
    id("moyi.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.moyi.app.designsystem.resources"
}
