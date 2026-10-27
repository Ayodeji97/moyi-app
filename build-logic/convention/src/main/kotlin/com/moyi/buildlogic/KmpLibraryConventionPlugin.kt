package com.moyi.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

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
            iosArm64()
            iosSimulatorArm64()

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
