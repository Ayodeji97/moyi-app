package com.moyi.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Allow-list of what a module may declare as a dependency.
 *
 * - [allowedProjects]: project paths, e.g. `:shared:core`.
 * - [allowedModules]: regular expressions matched against `group:name`.
 *   Defaults to the Kotlin standard library, kotlin-test and kotlinx-coroutines.
 */
abstract class DependencyGuardExtension {
    abstract val allowedProjects: SetProperty<String>
    abstract val allowedModules: ListProperty<String>
}

/**
 * `moyi.dependency.guard`: registers `checkDependencyGuard`, wired into `check`,
 * which fails when the module declares a dependency outside its allow-list.
 *
 * It reads every source set's `api`, `implementation`, `compileOnly` and
 * `runtimeOnly` configuration (commonMain, androidMain, iosMain, the test
 * source sets and the rest), so a framework cannot slip in through a
 * platform source set either.
 *
 * Configuration-cache safe: the declared dependencies are collected into a
 * plain `List<String>` task input while the build is configured. The task
 * action never touches `Project`.
 */
class DependencyGuardConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val guard = extensions.create<DependencyGuardExtension>("moyiDependencyGuard").apply {
            allowedModules.convention(
                listOf(
                    """org\.jetbrains\.kotlin:kotlin-stdlib(-.*)?""",
                    """org\.jetbrains\.kotlin:kotlin-test(-.*)?""",
                    """org\.jetbrains\.kotlinx:kotlinx-coroutines-.*""",
                ),
            )
            allowedProjects.convention(emptySet())
        }

        val declared = provider {
            val sourceSetConfigurations = extensions.findByType(KotlinMultiplatformExtension::class.java)
                ?.sourceSets
                ?.flatMap {
                    listOf(
                        it.apiConfigurationName,
                        it.implementationConfigurationName,
                        it.compileOnlyConfigurationName,
                        it.runtimeOnlyConfigurationName,
                    )
                }
                .orEmpty()
            val topLevel = listOf("api", "implementation", "compileOnly", "runtimeOnly")
            (sourceSetConfigurations + topLevel)
                .distinct()
                .mapNotNull { configurations.findByName(it) }
                .flatMap { configuration ->
                    configuration.dependencies.map { dependency ->
                        val coordinate = when (dependency) {
                            is ProjectDependency -> "project ${dependency.path}"
                            else -> "${dependency.group}:${dependency.name}"
                        }
                        "${configuration.name} -> $coordinate"
                    }
                }
                .sorted()
        }

        val checkTask = tasks.register<DependencyGuardTask>("checkDependencyGuard") {
            group = "verification"
            description = "Fails if ${target.path} declares a dependency outside its allow-list."
            modulePath.set(target.path)
            declaredDependencies.set(declared)
            allowedProjects.set(guard.allowedProjects)
            allowedModules.set(guard.allowedModules)
            report.set(layout.buildDirectory.file("reports/dependency-guard/report.txt"))
        }

        // Lazy: `matching` would realise every task in the module to test its name.
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            tasks.named("check") { dependsOn(checkTask) }
        }
    }
}
