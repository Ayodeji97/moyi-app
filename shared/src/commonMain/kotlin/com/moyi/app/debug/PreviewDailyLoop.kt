package com.moyi.app.debug

import com.moyi.app.domain.archive.ArchiveRepository
import com.moyi.app.domain.streak.StreakRepository
import com.moyi.app.domain.today.Prompt
import com.moyi.app.domain.today.TodayRepository
import com.moyi.app.feature.today.common.DailyLoop
import com.moyi.app.feature.today.common.DailyLoopState
import com.moyi.app.feature.today.common.DayView
import com.moyi.app.feature.today.common.DeleteResult
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * DEBUG ONLY. The day the Today preview runs on (`-PmoyiUiPreview=today`), so every
 * drawn layout (and the Archive, from the calendar target) can be reached and screenshotted with no backend and no session. Bound
 * over the real loop only in that build (KoinInit); nothing else ever sees it.
 *
 * It holds a layout, not a server's day, because one layout (Queued) has no server
 * shape yet: it needs the outbox. The long press on Today's date steps the layouts in
 * [TodayStatus] order; Write's send and the deletes move it the way the real day would.
 */
internal class PreviewDailyLoop(private val latency: Duration = PreviewLatency) : DailyLoop {
    private var myEntry = FakeCopy.MY_ENTRY
    private val _state = MutableStateFlow<DailyLoopState>(DailyLoopState.Ready(view(TodayStatus.NotWritten)))
    override val state: StateFlow<DailyLoopState> = _state.asStateFlow()

    private val status: TodayStatus get() = (_state.value as DailyLoopState.Ready).day.status

    override suspend fun refresh() = Unit

    override suspend fun submit(text: String, promptId: String?, idempotencyKey: String): WriteResult {
        delay(latency)
        myEntry = text
        show(TodayStatus.Waiting)
        return WriteResult.Saved
    }

    override suspend fun edit(text: String, idempotencyKey: String): WriteResult {
        delay(latency)
        myEntry = text
        show(status)
        return WriteResult.Saved
    }

    override suspend fun deleteBeforeReveal(): DeleteResult {
        show(TodayStatus.NotWritten)
        return DeleteResult.Deleted
    }

    override suspend fun deleteAfterReveal(): DeleteResult {
        delay(latency)
        show(TodayStatus.Tombstone)
        return DeleteResult.Deleted
    }

    /** The preview day is not anyone's: there is nothing to forget. */
    override fun clear() = Unit

    override fun debugNextLayout() {
        val all = TodayStatus.entries
        show(all[(all.indexOf(status) + 1) % all.size])
    }

    private fun show(status: TodayStatus) {
        _state.value = DailyLoopState.Ready(view(status))
    }

    private fun view(status: TodayStatus) = DayView(
        bondId = "preview-bond",
        date = if (status == TodayStatus.SoloYouWrote || status == TodayStatus.SoloTheyWrote) FakeCopy.SOLO_DATE else FakeCopy.DATE,
        status = status,
        partnerName = FakeCopy.PARTNER_NAME,
        prompt = Prompt("preview-prompt", FakeCopy.PROMPT),
        myEntryId = "preview-entry",
        myEntry = myEntry,
        // The same rule as the real loop: the partner's words only where a layout may show them.
        partnerEntry = FakeCopy.PARTNER_ENTRY.takeIf {
            status == TodayStatus.Revealed || status == TodayStatus.Tombstone || status == TodayStatus.SoloTheyWrote
        },
    )
}

/** How long a preview call takes, so Sending and Deleting are visible on a device. Not a design value. */
private val PreviewLatency: Duration = 800.milliseconds

/** Loaded after [appModules] in the today preview build, so its binding replaces the real loop. */
internal val previewTodayModule: Module = module {
    single<DailyLoop> { PreviewDailyLoop() }
    // The Archive's days, and the delete its day view sends (PreviewArchive.kt).
    single { PreviewArchiveRepository() }
    single<ArchiveRepository> { get<PreviewArchiveRepository>() }
    single<TodayRepository> { PreviewTodayRepository(get()) }
    // The Streak screen's four drawn states and a failure, in turn (PreviewStreak.kt).
    single<StreakRepository> { PreviewStreakRepository() }
}
