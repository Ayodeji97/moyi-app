package com.moyi.app.debug

import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.bond.BondSettings
import com.moyi.app.domain.bond.BondSettingsChange
import com.moyi.app.domain.bond.BondSettingsRepository
import com.moyi.app.domain.bond.BondStatus
import com.moyi.app.domain.bond.BondType
import com.moyi.app.domain.bond.MemberSettings
import com.moyi.app.domain.bond.TimeOfDay
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * DEBUG ONLY. The settings Bond settings runs on in the today preview
 * (`-PmoyiUiPreview=today`: Today's settings target opens it), with no backend.
 * Bound over the real repository only in that build (KoinInit).
 *
 * Every change saves after [latency], so Saving is visible on a device, and moves
 * the version as the server would. Leaving always succeeds.
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
    )
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

    override suspend fun leave(bondId: String): EmptyResult<DataError.Network> {
        delay(latency)
        return Result.Success(Unit)
    }

    override suspend fun memberSettings(bondId: String): Result<MemberSettings, DataError.Network> = Result.Success(member)

    override suspend fun replaceMemberSettings(bondId: String, settings: MemberSettings): Result<MemberSettings, DataError.Network> {
        member = settings
        return Result.Success(member)
    }
}
