package com.moyi.app.data.repository

import com.moyi.app.api.apis.BondMemberNotificationsControllerApi
import com.moyi.app.api.models.MemberNotificationsRequest
import com.moyi.app.api.models.MemberNotificationsResponse
import com.moyi.app.api.models.NotificationCategoriesRequest
import com.moyi.app.core.DataError
import com.moyi.app.core.Result
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
        safeApiCall { api.getMemberNotifications(bondId) }.mapOrSerialization { it.toDomain() }

    override suspend fun change(bondId: String, category: NotificationCategory, on: Boolean): Result<NotificationPreferences, DataError.Network> {
        val categories = when (category) {
            NotificationCategory.Reminder -> NotificationCategoriesRequest(reminder = on)
            NotificationCategory.Reveal -> NotificationCategoriesRequest(reveal = on)
            NotificationCategory.PartnerJoined -> NotificationCategoriesRequest(partnerJoined = on)
            NotificationCategory.Milestone -> NotificationCategoriesRequest(milestone = on)
            NotificationCategory.OnThisDay -> NotificationCategoriesRequest(onThisDay = on)
            NotificationCategory.Proposal -> NotificationCategoriesRequest(proposal = on)
        }
        return safeApiCall { api.replaceMemberNotifications(bondId, MemberNotificationsRequest(categories)) }
            .mapOrSerialization { it.toDomain() }
    }
}

/**
 * The server answers every toggle (`MemberNotificationsResponse`'s doc); the contract does
 * not mark them required (docs/backend-contract-gaps.md, rows 4 and 5). One missing is a
 * body that did not parse, never a guess at "off".
 */
internal fun MemberNotificationsResponse.toDomain(): NotificationPreferences? {
    val toggles = categories ?: return null
    val on = buildSet {
        if (toggles.reminder ?: return null) add(NotificationCategory.Reminder)
        if (toggles.reveal ?: return null) add(NotificationCategory.Reveal)
        if (toggles.partnerJoined ?: return null) add(NotificationCategory.PartnerJoined)
        if (toggles.milestone ?: return null) add(NotificationCategory.Milestone)
        if (toggles.onThisDay ?: return null) add(NotificationCategory.OnThisDay)
        if (toggles.proposal ?: return null) add(NotificationCategory.Proposal)
    }
    return NotificationPreferences(on)
}
