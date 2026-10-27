package com.moyi.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Compares declared dependencies (each `"<configuration> -> project :path"` or
 * `"<configuration> -> group:name"`) against an allow-list. Every input is a
 * plain value, so the result is cacheable and the configuration cache can
 * store the task without a `Project`.
 */
@CacheableTask
abstract class DependencyGuardTask : DefaultTask() {
    @get:Input abstract val modulePath: Property<String>
    @get:Input abstract val declaredDependencies: ListProperty<String>
    @get:Input abstract val allowedProjects: SetProperty<String>
    @get:Input abstract val allowedModules: ListProperty<String>
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val projects = allowedProjects.get()
        val modules = allowedModules.get().map(::Regex)
        val declared = declaredDependencies.get()

        val violations = declared.filterNot { line ->
            val coordinate = line.substringAfter(" -> ")
            if (coordinate.startsWith("project ")) {
                coordinate.removePrefix("project ") in projects
            } else {
                modules.any { it.matches(coordinate) }
            }
        }

        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("Declared dependencies of ${modulePath.get()}:")
                    declared.forEach { appendLine("  $it") }
                    appendLine("Violations: ${violations.size}")
                    violations.forEach { appendLine("  $it") }
                },
            )
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${modulePath.get()} may depend only on ${projects.sorted()} and on modules matching $modules.")
                    appendLine("These declared dependencies are not allowed:")
                    violations.forEach { appendLine("  $it") }
                    append("Domain code must stay free of frameworks; put this in a data or feature module instead.")
                },
            )
        }
    }
}
