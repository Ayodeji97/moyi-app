package com.moyi.app.architecture

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
}
