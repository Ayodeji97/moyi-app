package com.moyi.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import java.io.File

/**
 * `moyi.kmp.library`: a Kotlin Multiplatform library for Android, iosArm64 and
 * iosSimulatorArm64, using AGP 9's `com.android.kotlin.multiplatform.library`.
 *
 * The namespace comes from the module path, so two modules can never share one
 * (AGP 9 makes unique package names the default and fails on a clash).
 *
 * Klib and Kotlin module names are left to Gradle's default `group:name`,
 * where the default group is the parent path (`Moyi.shared.data` for
 * `:shared:data:api`). That makes them unique by path, so two leaf names
 * such as `api` can never clash in the iOS framework or the APK.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlinMultiplatform"))
        pluginManager.apply(libs.pluginId("androidMultiplatformLibrary"))

        extensions.configure<KotlinMultiplatformExtension> {
            // SQLDelight's iOS driver (shared/data/database) calls the `sqlite3_*` C API,
            // which Kotlin/Native does not link by itself. It must be SQLCipher's, never
            // the system SQLite's (-lsqlite3): with both linked, one set of symbols wins
            // silently and `PRAGMA key` may do nothing. The app's framework is static,
            // so Xcode links SQLCipher (the SQLCipher.swift package); the test
            // executables are linked here, against the same xcframework, with an rpath
            // to it so the simulator can load it when the tests run. -dead_strip_dylibs
            // drops the libsqlite3 load command a Kotlin/Native platform library adds
            // although no symbol binds to it, so `otool -L` shows SQLCipher alone.
            mapOf(iosArm64() to "ios-arm64", iosSimulatorArm64() to "ios-arm64_x86_64-simulator")
                .forEach { (target, slice) ->
                    val frameworks = File(sqlCipherDirectory(rootDir), "SQLCipher.xcframework/$slice").absolutePath
                    target.binaries.withType(TestExecutable::class.java).configureEach {
                        linkerOpts("-F", frameworks, "-framework", "SQLCipher", "-rpath", frameworks, "-dead_strip_dylibs")
                        linkTaskProvider.configure { dependsOn(SQLCIPHER_TASK) }
                    }
                }

            extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                namespace = moyiNamespace()
                compileSdk = libs.version("android-compileSdk").toInt()
                minSdk = libs.version("android-minSdk").toInt()
                // 11 matches :androidApp and the wizard. Nothing needs newer
                // bytecode yet; raising it is one line here when something does.
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
                // Runs commonTest on the JVM as part of `check`.
                withHostTest {}
            }

            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.library("kotlin-test"))
            }
        }
    }
}
