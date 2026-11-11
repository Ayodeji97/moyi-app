package com.moyi.app.data.repository

import com.moyi.app.api.apis.MemberReportsControllerApi
import com.moyi.app.api.apis.ReportsControllerApi
import com.moyi.app.api.models.ReportRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.domain.report.FiledReport
import com.moyi.app.domain.report.ReportReason
import com.moyi.app.domain.report.ReportRepository
import com.moyi.app.domain.report.ReportTarget
import com.moyi.app.domain.report.reportDetailOrNull

/**
 * [ReportRepository] over the generated report APIs, on the AUTHED client.
 *
 * Both routes take the same generated [ReportRequest] and the generated `Idempotency-Key`
 * header parameter (the contract declares it on the member route since backend #172;
 * docs/backend-contract-gaps.md row 35).
 */
class ReportRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : ReportRepository {
    private val entryApi = ReportsControllerApi(config.origin, authedClient.client)
    private val memberApi = MemberReportsControllerApi(config.origin, authedClient.client)

    override suspend fun report(
        target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        idempotencyKey: String,
    ): Result<FiledReport, DataError.Network> {
        val request = ReportRequest(reason = reason.toRequest(), detail = detail?.let(::reportDetailOrNull))
        return when (target) {
            is ReportTarget.Entry ->
                safeApiCall { entryApi.reportEntry(target.entryId, idempotencyKey, request) }
                    .mapOrSerialization { FiledReport(id = it.id, reason = ReportReason.valueOf(it.reason.name)) }
            is ReportTarget.Member ->
                safeApiCall { memberApi.reportMember(target.bondId, idempotencyKey, request) }
                    .mapOrSerialization { FiledReport(id = it.id, reason = ReportReason.valueOf(it.reason.name)) }
        }
    }
}

/** By name: the domain's five and the contract's five are the same list (ADR-0053 decision 3). */
internal fun ReportReason.toRequest(): ReportRequest.Reason = when (this) {
    ReportReason.NON_CONSENSUAL -> ReportRequest.Reason.NON_CONSENSUAL
    ReportReason.MINOR_SEXUAL_CONTENT -> ReportRequest.Reason.MINOR_SEXUAL_CONTENT
    ReportReason.VIOLENCE_OR_SELF_HARM -> ReportRequest.Reason.VIOLENCE_OR_SELF_HARM
    ReportReason.HARASSMENT -> ReportRequest.Reason.HARASSMENT
    ReportReason.OTHER -> ReportRequest.Reason.OTHER
}
