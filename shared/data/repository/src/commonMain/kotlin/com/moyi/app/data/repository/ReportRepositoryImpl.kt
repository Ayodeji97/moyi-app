package com.moyi.app.data.repository

import com.moyi.app.api.apis.MemberReportsControllerApi
import com.moyi.app.api.apis.ReportsControllerApi
import com.moyi.app.api.infrastructure.HttpResponse
import com.moyi.app.api.infrastructure.RequestConfig
import com.moyi.app.api.infrastructure.RequestMethod
import com.moyi.app.api.infrastructure.wrap
import com.moyi.app.api.models.MemberReportResponse
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
import io.ktor.client.HttpClient

/**
 * [ReportRepository] over the generated report APIs, on the AUTHED client.
 *
 * Both routes take the same generated [ReportRequest]. The member route is called
 * through [IdempotentMemberReportsApi], because the vendored contract leaves out the
 * `Idempotency-Key` header the server requires on it (docs/backend-contract-gaps.md
 * row 35): sent without it, every member report is `422`.
 */
class ReportRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : ReportRepository {
    private val entryApi = ReportsControllerApi(config.origin, authedClient.client)
    private val memberApi = IdempotentMemberReportsApi(config.origin, authedClient.client)

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

/**
 * The generated [MemberReportsControllerApi], with the one thing the contract leaves out:
 * the `Idempotency-Key` header, which the server requires on this route as on the entry
 * route (`@Idempotent` on `MemberReportsController.reportMember`; ADR-0053 decision 14).
 * The same request the generated `reportMember` builds, plus the header; the body and the
 * response are the generated models, so no DTO is written by hand.
 *
 * Delete this, and call the generated method, once the contract declares the header.
 */
internal class IdempotentMemberReportsApi(baseUrl: String, httpClient: HttpClient) :
    MemberReportsControllerApi(baseUrl, httpClient) {
    suspend fun reportMember(bondId: String, idempotencyKey: String, reportRequest: ReportRequest): HttpResponse<MemberReportResponse> {
        val config = RequestConfig<Any?>(
            RequestMethod.POST,
            "/api/v1/bonds/{bondId}/members/other/report".replace("{bondId}", bondId),
            headers = mutableMapOf(IDEMPOTENCY_KEY to idempotencyKey),
            requiresAuthentication = true,
        )
        return jsonRequest(config, reportRequest, listOf("bearerAuth")).wrap()
    }

    private companion object {
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
    }
}
