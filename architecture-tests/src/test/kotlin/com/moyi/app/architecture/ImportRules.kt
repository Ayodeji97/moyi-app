package com.moyi.app.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope

/**
 * The import rules, written as functions that return violations so the same
 * rule runs over the real sources and over a fixture that must fail it.
 */
object ImportRules {
    /** Frameworks the domain module must not know about (doc 13 §2, CLAUDE.md). */
    val forbiddenInDomain: List<String> = listOf(
        "io.ktor",
        "app.cash.sqldelight",
        "android",
        "androidx",
        "platform",
        "org.koin",
        "kotlinx.serialization",
        "com.moyi.app.data",
    )

    /**
     * Features talk to the domain, never to the data layer directly, and never to
     * the generated network DTOs (`com.moyi.app.api`, PR #3): those are mapped to
     * domain models in the repository layer.
     */
    val forbiddenInFeatures: List<String> = listOf("com.moyi.app.data", "com.moyi.app.api")

    /**
     * Kotlin sources under [directory] (relative to the repository root), with
     * anything under a `build/` directory left out: generated code is not ours.
     */
    fun scope(directory: String): KoScope =
        Konsist.scopeFromDirectory(directory).slice { "/build/" !in it.path }

    /** `"<file>: import <name>"` for each import that starts with a forbidden package. */
    fun violations(scope: KoScope, forbiddenPackages: List<String>): List<String> =
        scope.files.flatMap { file ->
            file.imports
                .filter { import -> forbiddenPackages.any { import.name == it || import.name.startsWith("$it.") } }
                .map { "${file.path}: import ${it.name}" }
        }
}
