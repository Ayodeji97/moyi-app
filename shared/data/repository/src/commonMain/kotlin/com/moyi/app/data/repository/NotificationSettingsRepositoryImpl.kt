package com.moyi.app.data.repository

import com.moyi.app.api.apis.BondMemberNotificationsControllerApi
import com.moyi.app.api.models.MemberNotificationsRequest
import com.moyi.app.api.models.MemberNotificationsResponse
import com.moyi.app.api.models.NotificationCategoriesRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
import com.moyi.app.core.map
import com.moyi.app.data.network.ApiConfig
import com.moyi.app.data.network.AuthedHttpClient
import com.moyi.app.data.network.safeApiCall
import com.moyi.app.domain.notification.NotificationCategory
import com.moyi.app.domain.notification.NotificationPreferences
import com.moyi.app.domain.notification.NotificationSettingsRepository

/**
 * [NotificationSettingsRepository] over the generated member-notifications API, on the
 * AUTHED client.
 *
 * A change names only its own toggle and leaves the other five out of the body (null,
 * which the server reads as "keep", ADR-0045): a toggle this client does not know yet is
 * never turned off by it.
 */
class NotificationSettingsRepositoryImpl(
    authedClient: AuthedHttpClient,
    config: ApiConfig,
) : NotificationSettingsRepository {
    private val api = BondMemberNotificationsControllerApi(config.origin, authedClient.client)

    override suspend fun preferences(bondId: String): Result<NotificationPreferences, DataError.Network> =
        safeApiCall { api.getMemberNotifications(bondId) }.map { it.toDomain() }

    override suspend fun change(bondId: String, category: NotificationCategory, on: Boolean): Result<NotificationPreferences, DataError.Network> {
        val categories = when (category) {
            NotificationCategory.Reminder -> NotificationCategoriesRequest(reminder = on)
            NotificationCategory.Reveal -> NotificationCategoriesRequest(reveal = on)
            NotificationCategory.PartnerJoined -> NotificationCategoriesRequest(partnerJoined = on)
            NotificationCategory.Milestone -> NotificationCategoriesRequest(milestone = on)
            NotificationCategory.OnThisDay -> NotificationCategoriesRequest(onThisDay = on)
            NotificationCategory.Proposal -> NotificationCategoriesRequest(proposal = on)
        }
        return safeApiCall { api.replaceMemberNotifications(bondId, MemberNotificationsRequest(categories = categories)) }
            .map { it.toDomain() }
    }
}

/**
 * The server answers every toggle, and the contract marks them required, so a body
 * missing one fails to decode: never a guess at "off".
 */
internal fun MemberNotificationsResponse.toDomain(): NotificationPreferences {
    val toggles = categories
    val on = buildSet {
        if (toggles.reminder) add(NotificationCategory.Reminder)
        if (toggles.reveal) add(NotificationCategory.Reveal)
        if (toggles.partnerJoined) add(NotificationCategory.PartnerJoined)
        if (toggles.milestone) add(NotificationCategory.Milestone)
        if (toggles.onThisDay) add(NotificationCategory.OnThisDay)
        if (toggles.proposal) add(NotificationCategory.Proposal)
    }
    return NotificationPreferences(on)
}
