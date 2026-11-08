plugins {
    id("moyi.kmp.library")
    alias(libs.plugins.sqldelight)
}

// The local database (doc 13 §2 and §5): SQLDelight 2.x, one SQLite file per
// install. It holds what Today shows offline and nothing that is not the signed-in
// person's own; the session wipes all of it when a session ends or a new one starts
// (LocalDatabase.wipe). It knows no network, no session and no domain: the
// repository layer maps between its rows and domain models.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.sqldelight.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
        commonTest.dependencies {
            implementation(project(":shared:data:testing"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("MoyiDatabase") {
            packageName.set("com.moyi.app.data.database")
            // The .sq files are the schema a fresh install creates; each numbered
            // .sqm file (1.sqm: version 1 to 2) carries a phone that already has the
            // database forward. Version 1 (cached_day) is on development phones
            // since #37, so the draft table arrives by migration (1.sqm), not by
            // reinstall, and the outbox the same way (2.sqm, version 2 to 3).
        }
    }
}
