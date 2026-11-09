package com.moyi.app.feature.auth

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.legal.AcceptedVersions
import com.moyi.app.domain.legal.LegalDocuments
import com.moyi.app.domain.legal.LegalRepository
import com.moyi.app.domain.legal.LegalStatus
import com.moyi.app.domain.legal.PublishedDocument
import kotlinx.coroutines.CompletableDeferred

val testLegalDocuments = LegalDocuments(
    terms = PublishedDocument("local-2", "https://example.test/terms"),
    privacy = PublishedDocument("local-2", "https://example.test/privacy"),
    dataProcessingSummary = PublishedDocument("local-1", "https://example.test/data"),
)

/** An in-memory [LegalRepository]: each call returns its `…Result` and is counted. */
class FakeLegalRepository : LegalRepository {
    var documentsResult: Result<LegalDocuments, DataError.Network> = Result.Success(testLegalDocuments)
    var statusResult: Result<LegalStatus, DataError.Network> =
        Result.Success(LegalStatus(testLegalDocuments, AcceptedVersions(terms = "local-1", privacy = "local-1")))
    var acceptResult: EmptyResult<DataError.Network> = Result.Success(Unit)

    /** Holds acceptCurrent() in flight until completed, to observe Sending. */
    var acceptGate: CompletableDeferred<Unit>? = null

    var documentsCalls = 0
    var statusCalls = 0
    var acceptCalls = 0

    override suspend fun documents(): Result<LegalDocuments, DataError.Network> {
        documentsCalls++
        return documentsResult
    }

    override suspend fun status(): Result<LegalStatus, DataError.Network> {
        statusCalls++
        return statusResult
    }

    override suspend fun acceptCurrent(): EmptyResult<DataError.Network> {
        acceptCalls++
        acceptGate?.await()
        return acceptResult
    }
}
