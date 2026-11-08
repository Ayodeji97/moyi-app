package com.moyi.app.debug

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.Bond
import com.moyi.app.domain.bond.BondRepository
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.Invite
import com.moyi.app.domain.bond.InvitePreview
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.PendingDeletionRequest
import com.moyi.app.domain.bond.PendingTimezoneChange
import com.moyi.app.domain.bond.TimeOfDay
import com.moyi.app.domain.notification.NotificationCategory
import com.moyi.app.domain.notification.NotificationPreferences
import com.moyi.app.domain.notification.NotificationSettingsRepository
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * DEBUG ONLY. The settings Bond settings runs on in the today preview
 * (`-PmoyiUiPreview=today`: Today's settings target opens it), with no backend.
 * Bound over the real repository only in that build (KoinInit).
 *
 * Every change saves after [latency], so Saving is visible on a device, and moves
 * the version as the server would. Leaving always succeeds.
 *
 * The shared time zone starts with Tunde's proposal waiting (Confirm). Agree moves the
 * zone, and any proposal after that is the 30-day refusal. Decline instead leads to
 * Propose; proposing then waits (Proposed) until it is cancelled. The bond holds a live
 * invite, so the Invite row shows on this ACTIVE bond: cancelling it is Revoked, and
 * creating one is Active again with a new code.
 *
 * Tunde has asked to close the box (Agree). Agreeing starts the cooling-off, 30 days
 * from now; Keep the box calls it off; asking again then waits on Tunde (Asked) until
 * cancelled. Leave and Block and leave end the bond (ARCHIVED), so the signed-in start
 * shows the archived bond.
 */
internal class PreviewBondSettingsRepository(private val latency: Duration = 800.milliseconds) : BondSettingsRepository {
    private var settings = BondSettings(
        bondId = "preview-bond",
        name = "Amara and Tunde",
        type = BondType.COUPLE,
        status = BondStatus.ACTIVE,
        anchorTimezone = "Africa/Lagos",
        revealTime = null,
        strictMode = false,
        version = "\"1\"",
        partnerName = "Tunde",
        pendingTimezone = PendingTimezoneChange("preview-proposal", "Europe/London", proposedByMe = false),
        invite = previewInvite("K7Q2PD"),
        // Tunde asked to close the box: Bond settings › Close the box opens on Agree.
        pendingDeletion = PendingDeletionRequest(requestedByMe = false, expiresAt = Clock.System.now() + 7.days),
    )
    private var zoneChanged = false
    private var inviteCount = 0
    private var member = MemberSettings(TimeOfDay(20, 0), "Africa/Lagos")
    private var version = 1

    override suspend fun settings(bondId: String): Result<BondSettings, DataError.Network> = Result.Success(settings)

    override suspend fun update(bondId: String, version: String, change: BondSettingsChange): Result<BondSettings, DataError.Network> {
        delay(latency)
        settings = settings.copy(
            name = change.name ?: settings.name,
            type = change.type ?: settings.type,
            revealTime = change.revealTime ?: settings.revealTime,
            strictMode = change.strictMode ?: settings.strictMode,
            version = "\"${++this.version}\"",
        )
        return Result.Success(settings)
    }

    override suspend fun leave(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network> = end(withdrawEntries)

    override suspend fun block(bondId: String, withdrawEntries: Boolean): EmptyResult<DataError.Network> = end(withdrawEntries)

    /** Leave and block end it the same way: ARCHIVED, so the signed-in start shows the archived bond. */
    private suspend fun end(@Suppress("UNUSED_PARAMETER") withdrawEntries: Boolean): EmptyResult<DataError.Network> {
        delay(latency)
        settings = settings.copy(status = BondStatus.ARCHIVED, pendingDeletion = null, deletionScheduledFor = null, invite = null)
        return Result.Success(Unit)
    }

    /** Asks, or agrees to Tunde's ask, which starts the 30-day cooling-off. */
    override suspend fun requestDeletion(bondId: String): Result<BondSettings, DataError.Network> {
        delay(latency)
        val theirs = settings.pendingDeletion?.requestedByMe == false
        settings = if (theirs) {
            settings.copy(status = BondStatus.PENDING_DELETION, pendingDeletion = null, deletionScheduledFor = Clock.System.now() + 30.days, version = next())
        } else {
            settings.copy(pendingDeletion = PendingDeletionRequest(requestedByMe = true, expiresAt = Clock.System.now() + 7.days), version = next())
        }
        return Result.Success(settings)
    }

    override suspend fun cancelDeletion(bondId: String): EmptyResult<DataError.Network> {
        delay(latency)
        settings = settings.copy(status = BondStatus.ACTIVE, pendingDeletion = null, deletionScheduledFor = null, version = next())
        return Result.Success(Unit)
    }

    override suspend fun proposeTimezone(bondId: String, timezone: String): Result<BondSettings, DataError.Network> {
        delay(latency)
        if (zoneChanged) return Result.Failure(DataError.Network.Api(ApiProblem(409, ApiErrorCode.TIMEZONE_CHANGE_TOO_SOON)))
        settings = settings.copy(pendingTimezone = PendingTimezoneChange("preview-mine", timezone, proposedByMe = true), version = next())
        return Result.Success(settings)
    }

    override suspend fun confirmTimezone(bondId: String, proposalId: String): Result<BondSettings, DataError.Network> {
        delay(latency)
        val pending = settings.pendingTimezone ?: return Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.NOT_FOUND)))
        zoneChanged = true
        settings = settings.copy(anchorTimezone = pending.timezone, pendingTimezone = null, version = next())
        return Result.Success(settings)
    }

    override suspend fun cancelTimezoneChange(bondId: String): EmptyResult<DataError.Network> {
        delay(latency)
        settings = settings.copy(pendingTimezone = null)
        return Result.Success(Unit)
    }

    /** The invite half of [BondRepository], over the same preview bond. */
    fun revokeInvite() {
        settings = settings.copy(invite = null)
    }

    fun createInvite(): Invite = previewInvite(listOf("M4XR9T", "P8HD3W", "Q2ZN6K")[inviteCount++ % 3]).also { settings = settings.copy(invite = it) }

    private fun next(): String = "\"${++version}\""

    private fun previewInvite(code: String) = Invite("preview-invite-$code", code, "https://getmoyi.app/i/$code", Clock.System.now() + 7.days)

    override suspend fun memberSettings(bondId: String): Result<MemberSettings, DataError.Network> = Result.Success(member)

    override suspend fun replaceMemberSettings(bondId: String, settings: MemberSettings): Result<MemberSettings, DataError.Network> {
        member = settings
        return Result.Success(member)
    }
}

/**
 * DEBUG ONLY. The invite calls of [BondRepository] over [settings]' preview bond, for the
 * today preview build. Onboarding's calls are not reached from there and answer nothing.
 */
internal class PreviewSettingsBondRepository(private val settings: PreviewBondSettingsRepository) : BondRepository {
    override suspend fun bonds(): Result<List<Bond>, DataError.Network> {
        val current = (settings.settings("preview-bond") as Result.Success).data
        return Result.Success(listOf(Bond(current.bondId, current.name, current.type, current.status, current.invite, current.partnerName)))
    }

    override suspend fun createInvite(bondId: String): Result<Invite, DataError.Network> {
        delay(800.milliseconds)
        return Result.Success(settings.createInvite())
    }

    override suspend fun revokeInvite(bondId: String, inviteId: String): EmptyResult<DataError.Network> {
        delay(800.milliseconds)
        settings.revokeInvite()
        return Result.Success(Unit)
    }

    override suspend fun createBond(name: String, type: BondType, anchorTimezone: String): Result<Bond, DataError.Network> = unused

    override suspend fun resolveInvite(code: String): Result<InvitePreview, DataError.Network> = unused

    override suspend fun acceptInvite(code: String): Result<Bond, DataError.Network> = unused

    private val unused = Result.Failure(DataError.Network.Unknown)
}

/** DEBUG ONLY. Notifications on the preview bond, kept in memory: the preview's member never chose, so the defaults. */
class PreviewNotificationSettingsRepository : NotificationSettingsRepository {
    private var preferences = NotificationPreferences(NotificationCategory.entries.toSet() - NotificationCategory.OnThisDay)

    override suspend fun preferences(bondId: String): Result<NotificationPreferences, DataError.Network> = Result.Success(preferences)

    override suspend fun change(bondId: String, category: NotificationCategory, on: Boolean): Result<NotificationPreferences, DataError.Network> {
        delay(600.milliseconds) // long enough to see Saving
        preferences = NotificationPreferences(if (on) preferences.on + category else preferences.on - category)
        return Result.Success(preferences)
    }
}
