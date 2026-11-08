plugins {
    id("moyi.kmp.library")
}

// Test fixtures shared by the data modules' commonTest source sets: the
// in-memory token store, an ordered event log, MockEngine helpers and an
// in-memory database. KMP has
// no testFixtures source set, so this is a module that only tests depend on.
// Nothing in production code may depend on it.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            api(project(":shared:data:session"))
            api(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.core)
            api(project(":shared:data:database"))
        }
        // An in-memory database per test (inMemoryDatabase): the JVM's SQLite on the
        // Android host tests, the system SQLite on the iOS simulator.
        androidMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
    }
}
