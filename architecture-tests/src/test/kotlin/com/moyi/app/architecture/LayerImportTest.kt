package com.moyi.app.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LayerImportTest {
    @Test
    fun `domain imports no framework and no data layer`() {
        val scope = ImportRules.scope("shared/domain/src")
        // An empty scope would pass vacuously, e.g. after a directory move.
        assertTrue(scope.files.isNotEmpty(), "No Kotlin files found under shared/domain/src")

        val violations = ImportRules.violations(scope, ImportRules.forbiddenInDomain)
        assertTrue(violations.isEmpty(), "Domain must stay framework-free:\n" + violations.joinToString("\n"))
    }

    @Test
    fun `feature modules do not import the data layer`() {
        val scope = ImportRules.scope("shared/feature")
        assertTrue(scope.files.isNotEmpty(), "No Kotlin files found under shared/feature")

        val violations = ImportRules.violations(scope, ImportRules.forbiddenInFeatures)
        assertTrue(violations.isEmpty(), "Features go through the domain:\n" + violations.joinToString("\n"))
    }

    /**
     * The rule above reads `shared/feature` as a directory, so a new feature
     * module is covered without editing it. This proves it: every module under
     * `shared/feature` (auth, onboarding and today) contributes files to the scope.
     * A module whose sources sat somewhere the scope cannot see would fail here.
     */
    @Test
    fun `every feature module is inside the feature rule's scope`() {
        val scope = ImportRules.scope("shared/feature")
        val modules = File(repoRoot, "shared/feature").listFiles { f -> File(f, "build.gradle.kts").exists() }
            .orEmpty().map { it.name }.sorted()
        assertTrue(listOf("auth", "onboarding", "today").all { it in modules }, "Expected auth, onboarding and today under shared/feature, found $modules")

        val uncovered = modules.filter { module -> scope.files.none { "/shared/feature/$module/src/" in it.path } }
        assertTrue(uncovered.isEmpty(), "Feature modules with no files in the import rule's scope: $uncovered")
    }

    private val repoRoot: File
        get() = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() && File(it, "shared").isDirectory }
}
