package com.moyi.app.feature.auth.common

import com.moyi.app.feature.auth.LegalConfig

/** The two documents sign up links to, under the consent row. */
enum class LegalDocument { Terms, Privacy }

/**
 * Where the Terms and the Privacy Policy live. Null while unpublished: the
 * link then does nothing rather than open a page that does not exist.
 *
 * From the build ([fromBuild]: `moyi.termsUrl`, `moyi.privacyUrl`) until the
 * API serves them (`GET /meta/legal`, doc 06 §3.7, not yet in the contract).
 */
data class LegalLinks(val terms: String?, val privacy: String?) {
    fun urlFor(document: LegalDocument): String? = when (document) {
        LegalDocument.Terms -> terms
        LegalDocument.Privacy -> privacy
    }

    companion object {
        fun fromBuild(): LegalLinks = LegalLinks(
            terms = LegalConfig.TERMS_URL.ifBlank { null },
            privacy = LegalConfig.PRIVACY_URL.ifBlank { null },
        )
    }
}
