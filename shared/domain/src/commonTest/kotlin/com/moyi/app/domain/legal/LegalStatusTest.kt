package com.moyi.app.domain.legal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalStatusTest {
    private val current = LegalDocuments(
        terms = PublishedDocument("local-2", "https://example.test/terms"),
        privacy = PublishedDocument("local-1", "https://example.test/privacy"),
        dataProcessingSummary = PublishedDocument("local-9", "https://example.test/data"),
    )

    @Test
    fun bothCurrentVersionsAcceptedIsUpToDate() {
        assertTrue(LegalStatus(current, AcceptedVersions(terms = "local-2", privacy = "local-1")).upToDate)
    }

    @Test
    fun anOlderTermsVersionIsNotUpToDate() {
        assertFalse(LegalStatus(current, AcceptedVersions(terms = "local-1", privacy = "local-1")).upToDate)
    }

    @Test
    fun anOlderPrivacyVersionIsNotUpToDate() {
        assertFalse(LegalStatus(current, AcceptedVersions(terms = "local-2", privacy = "local-0")).upToDate)
    }

    @Test
    fun nothingAcceptedIsNotUpToDate() {
        assertFalse(LegalStatus(current, AcceptedVersions(terms = null, privacy = null)).upToDate)
    }

    @Test
    fun theSummaryIsNeverPartOfTheComparison() {
        // Shown, never accepted on its own (GET /meta/legal): its version is not compared.
        assertTrue(LegalStatus(current, AcceptedVersions(terms = "local-2", privacy = "local-1")).upToDate)
    }

    @Test
    fun versionsAreComparedAsLabelsNeverParsed() {
        val tenth = current.copy(terms = PublishedDocument("local-10", "https://example.test/terms"))
        assertFalse(LegalStatus(tenth, AcceptedVersions(terms = "local-2", privacy = "local-1")).upToDate)
    }
}
