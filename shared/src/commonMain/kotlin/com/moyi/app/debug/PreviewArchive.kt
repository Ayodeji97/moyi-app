package com.moyi.app.debug

import com.moyi.app.core.ApiErrorCode
import com.moyi.app.core.ApiProblem
import com.moyi.app.core.DataError
import com.moyi.app.core.EmptyResult
import com.moyi.app.core.Result
import com.moyi.app.domain.archive.ArchiveDay
import com.moyi.app.domain.archive.ArchivePage
import com.moyi.app.domain.archive.ArchiveRepository
import com.moyi.app.domain.today.BondDayStatus
import com.moyi.app.domain.today.EntryStatus
import com.moyi.app.domain.today.MyEntry
import com.moyi.app.domain.today.PartnerEntry
import com.moyi.app.domain.today.Today
import com.moyi.app.domain.today.TodayRepository
import com.moyi.app.feature.today.common.FakeCopy
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlin.time.Duration.Companion.milliseconds

/**
 * DEBUG ONLY. The archive the Today preview runs on (`-PmoyiUiPreview=today`): thirty
 * days, newest first, served eight at a time with a cursor, so paging can be watched;
 * the newest day carries a long entry (states.md §6's 481-character case) for the day
 * view. Every shape the list draws is in it: revealed, solo with their words, my
 * tombstone, and a day still waiting (its partner entry locked, so it shows mine only).
 * Bound over the real repositories only in that build (KoinInit); nothing else sees it.
 */
internal class PreviewArchiveRepository : ArchiveRepository {
    private val days: MutableList<ArchiveDay> = (0 until DAY_COUNT).map { offset ->
        val date = FakeCopy.DATE.minus(DatePeriod(days = offset)).toString()
        val mine = MyEntry("preview-mine-$offset", PREVIEW_MINE[offset % PREVIEW_MINE.size], EntryStatus.REVEALED)
        val theirs = PartnerEntry.Revealed("preview-theirs-$offset", PREVIEW_THEIRS[offset % PREVIEW_THEIRS.size])
        when (offset) {
            0 -> ArchiveDay(date, BondDayStatus.REVEALED, mine, PartnerEntry.Revealed("preview-theirs-0", LONG_ENTRY))
            1 -> ArchiveDay(date, BondDayStatus.SOLO, null, PartnerEntry.Submitted("preview-theirs-1", FakeCopy.PARTNER_ENTRY))
            2 -> ArchiveDay(date, BondDayStatus.REVEALED, mine.copy(text = null, status = EntryStatus.DELETED), theirs)
            3 -> ArchiveDay(date, BondDayStatus.PENDING_REVEAL, mine.copy(status = EntryStatus.SUBMITTED), PartnerEntry.Locked)
            else -> ArchiveDay(date, BondDayStatus.REVEALED, mine, theirs)
        }
    }.toMutableList()

    override suspend fun days(bondId: String, cursor: String?): Result<ArchivePage, DataError.Network> {
        delay(PREVIEW_LATENCY)
        val start = cursor?.toIntOrNull() ?: 0
        val end = minOf(start + PAGE_SIZE, days.size)
        return Result.Success(ArchivePage(days.subList(start, end).toList(), nextCursor = end.takeIf { it < days.size }?.toString()))
    }

    override suspend fun day(bondId: String, date: String): Result<ArchiveDay, DataError.Network> =
        days.firstOrNull { it.date == date }?.let { Result.Success(it) }
            ?: Result.Failure(DataError.Network.Api(ApiProblem(404, ApiErrorCode.NOT_FOUND)))

    /** A delete from the day view: the tombstone stays, as after the server's 204. */
    fun deleted(entryId: String) {
        val index = days.indexOfFirst { it.myEntry?.id == entryId }
        if (index >= 0) days[index] = days[index].copy(myEntry = days[index].myEntry?.copy(text = null, status = EntryStatus.DELETED))
    }

    private companion object {
        const val DAY_COUNT = 30
        const val PAGE_SIZE = 8
        val PREVIEW_LATENCY = 800.milliseconds

        val PREVIEW_MINE = listOf(
            FakeCopy.MY_ENTRY,
            "I love how you remember the little things.",
            "You never once made me feel late, even when I was very late.",
        )
        val PREVIEW_THEIRS = listOf(
            "You always make the distance feel smaller. Even on the hard days, I feel like we are on the same team.",
            "You made the flat smell like home again before I even got through the door.",
        )

        /** About 480 characters, the length states.md §6 measured the day view with. */
        const val LONG_ENTRY =
            "You stayed up to help me finish it, and never once made me feel slow. I noticed. I always notice. " +
                "When the printer jammed at midnight you just laughed and went to find the old one in the cupboard, " +
                "and when that one ran out of ink you walked to the shop in the rain and came back with two cartridges " +
                "and a bag of the biscuits I like. You did not say a word about it in the morning, you just asked how " +
                "it went. It went well. It went well because of you, and I wanted you to know that I saw it all."
    }
}

/**
 * DEBUG ONLY. The today preview's TodayRepository: only [deleteEntry] is reached (the
 * Archive's delete; Today itself runs on PreviewDailyLoop). It always succeeds, after a
 * moment, so the disabled link while it is in flight can be seen.
 */
internal class PreviewTodayRepository(private val archive: PreviewArchiveRepository) : TodayRepository {
    override suspend fun today(bondId: String): Result<Today, DataError.Network> = Result.Failure(DataError.Network.Unknown)

    override suspend fun submitEntry(bondId: String, text: String, promptId: String?, idempotencyKey: String): Result<MyEntry, DataError.Network> =
        Result.Failure(DataError.Network.Unknown)

    override suspend fun editEntry(entryId: String, text: String, idempotencyKey: String): Result<MyEntry, DataError.Network> =
        Result.Failure(DataError.Network.Unknown)

    override suspend fun deleteEntry(entryId: String): EmptyResult<DataError.Network> {
        delay(800.milliseconds)
        archive.deleted(entryId)
        return Result.Success(Unit)
    }
}
