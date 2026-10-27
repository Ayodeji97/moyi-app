package com.moyi.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `moyi.kmp.compose`: `moyi.kmp.library` plus Compose Multiplatform, the Compose
 * compiler, the standard UI dependencies and Android resources.
 *
 * Android resources must be switched on explicitly with the KMP library plugin;
 * without them Compose resources crash at runtime on Android.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(KmpLibraryConventionPlugin::class.java)
        pluginManager.apply(libs.pluginId("composeMultiplatform"))
        pluginManager.apply(libs.pluginId("composeCompiler"))

        extensions.configure<KotlinMultiplatformExtension> {
            extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                androidResources { enable = true }
            }

            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.library("compose-runtime"))
                implementation(libs.library("compose-foundation"))
                implementation(libs.library("compose-material3"))
                implementation(libs.library("compose-ui"))
                implementation(libs.library("compose-components-resources"))
                implementation(libs.library("compose-uiToolingPreview"))
                implementation(libs.library("androidx-lifecycle-viewmodelCompose"))
                implementation(libs.library("androidx-lifecycle-runtimeCompose"))
            }
            sourceSets.getByName("androidMain").dependencies {
                implementation(libs.library("compose-uiToolingPreview"))
            }
        }

        // Previews render in the IDE from the Android runtime classpath.
        dependencies {
            add("androidRuntimeClasspath", libs.library("compose-uiTooling"))
        }
    }
}
