plugins {
    id("moyi.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
            implementation(project(":shared:data:api"))
            implementation(project(":shared:data:network"))
            implementation(project(":shared:data:session"))
        }
    }
}
