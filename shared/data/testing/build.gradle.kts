plugins {
    id("moyi.kmp.library")
}

// Test fixtures shared by the data modules' commonTest source sets: the
// in-memory token store, an ordered event log and MockEngine helpers. KMP has
// no testFixtures source set, so this is a module that only tests depend on.
// Nothing in production code may depend on it.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            api(project(":shared:data:session"))
            api(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
