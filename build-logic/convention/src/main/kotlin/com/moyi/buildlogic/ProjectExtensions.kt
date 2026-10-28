package com.moyi.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/**
 * The root version catalog. Convention plugins cannot use the generated `libs`
 * accessors, so they look entries up by name.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalStateException("No library '$alias' in libs.versions.toml") }

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("No version '$alias' in libs.versions.toml") }
        .requiredVersion

internal fun VersionCatalog.pluginId(alias: String): String =
    findPlugin(alias).orElseThrow { IllegalStateException("No plugin '$alias' in libs.versions.toml") }
        .get().pluginId

/**
 * `:shared:data:network` -> `com.moyi.app.data.network`. The `shared` segment is
 * dropped: every module lives under it, so it says nothing. The umbrella
 * `:shared` itself becomes `com.moyi.app.shared`.
 */
internal fun Project.moyiNamespace(): String {
    val segments = path.removePrefix(":").split(":")
    val meaningful = if (segments.size > 1 && segments.first() == "shared") segments.drop(1) else segments
    return "com.moyi.app." + meaningful.joinToString(".") { it.replace("-", "") }
}
