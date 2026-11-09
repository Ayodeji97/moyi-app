package com.moyi.app.feature.auth.common

import com.moyi.app.domain.legal.LegalDocuments

/**
 * The documents the app links to. Sign up links the two a person accepts; the Terms and
 * Privacy screen adds the data-processing summary, which is shown and never accepted.
 */
enum class LegalDocument { Terms, Privacy, DataProcessing }

/** Where [document] is read now, as `GET /meta/legal` names it. */
fun LegalDocuments.urlFor(document: LegalDocument): String = when (document) {
    LegalDocument.Terms -> terms.url
    LegalDocument.Privacy -> privacy.url
    LegalDocument.DataProcessing -> dataProcessingSummary.url
}
