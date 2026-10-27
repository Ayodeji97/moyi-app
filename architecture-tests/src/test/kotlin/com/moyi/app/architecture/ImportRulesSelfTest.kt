package com.moyi.app.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the rules fire. Each fixture is a deliberately violating .kt file
 * kept under test resources (so it is never compiled). If Konsist ever stops
 * parsing our Kotlin version, or a rule is weakened, these fail instead of the
 * real rules passing silently.
 */
class ImportRulesSelfTest {
    private val fixtures = "architecture-tests/src/test/resources/fixtures"

    @Test
    fun `domain rule reports every forbidden import in the fixture`() {
        val violations = ImportRules.violations(
            ImportRules.scope("$fixtures/domain"),
            ImportRules.forbiddenInDomain,
        )
        val imports = violations.map { it.substringAfter("import ") }.toSet()
        assertEquals(
            setOf(
                "io.ktor.client.HttpClient",
                "android.os.Build",
                "androidx.lifecycle.ViewModel",
                "platform.UIKit.UIDevice",
                "org.koin.core.module.Module",
                "kotlinx.serialization.Serializable",
                "app.cash.sqldelight.db.SqlDriver",
                "com.moyi.app.data.network.ModuleInfo",
            ),
            imports,
        )
    }

    @Test
    fun `domain rule allows the core module and coroutines`() {
        val violations = ImportRules.violations(
            ImportRules.scope("$fixtures/domain"),
            ImportRules.forbiddenInDomain,
        )
        val allowed = setOf("com.moyi.app.core.Result", "kotlinx.coroutines.flow.Flow", "androidxish.Fake")
        // The allowed imports must actually be in the fixture, or this test would pass vacuously.
        val parsed = ImportRules.scope("$fixtures/domain").files.flatMap { file -> file.imports.map { it.name } }.toSet()
        assertTrue(parsed.containsAll(allowed), "fixture is missing allowed imports: ${allowed - parsed}")
        assertEquals(emptyList(), violations.filter { it.substringAfter("import ") in allowed })
    }

    @Test
    fun `feature rule reports the data import in the fixture`() {
        val violations = ImportRules.violations(
            ImportRules.scope("$fixtures/feature"),
            ImportRules.forbiddenInFeatures,
        )
        assertEquals(
            setOf("com.moyi.app.data.repository.ModuleInfo", "com.moyi.app.api.models.LoginResponse"),
            violations.map { it.substringAfter("import ") }.toSet(),
        )
    }
}
