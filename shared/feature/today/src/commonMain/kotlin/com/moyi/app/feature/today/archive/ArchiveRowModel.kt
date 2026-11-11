package com.moyi.app.feature.today.archive

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/** Who wrote a line on an Archive row. */
internal enum class RowAuthor { You, Partner }

/** What a row line says. Never holds more than the first line of the words. */
internal sealed interface RowBody {
    /** The first line of the words (see [firstLine]). */
    data class Words(val line: String) : RowBody

    /** An entry with no words, only media: the line names the media instead of being blank. */
    data class MediaOnly(val photo: Boolean, val voice: Boolean) : RowBody

    /** My entry, deleted after the reveal: its tombstone. */
    data object Deleted : RowBody
}

/** One entry on a row. [photo] and [voice] say what the entry carries, whether or not it has words. */
@Immutable
internal data class RowLine(val author: RowAuthor, val body: RowBody, val photo: Boolean, val voice: Boolean)

/**
 * A day as one compact Archive row. Derived from [ArchiveDayUi] by [toRowModel]; pure and
 * cheap, so the list builds it once per day (`remember(day)`) and a row does no other work.
 * The words are the person's own: never printed by `toString`.
 */
@Immutable
internal data class ArchiveRowModel(
    val date: LocalDate,
    /** Mine first, then the partner's (the owner's example reads "You: ... Ermb: ..."). */
    val lines: List<RowLine>,
    val favourite: Boolean,
) {
    /** The chips: media that a line with words carries. A media-only line already says it. */
    val photoChip: Boolean get() = lines.any { it.photo && it.body is RowBody.Words }
    val voiceChip: Boolean get() = lines.any { it.voice && it.body is RowBody.Words }

    override fun toString(): String = "ArchiveRowModel(date=$date, lines=${lines.size}, favourite=$favourite)"
}

/**
 * The first line of [text] for a row: trimmed, then up to the first line break, trimmed
 * again. Null when nothing is left, so the caller falls back to the media label.
 */
internal fun firstLine(text: String?): String? =
    text?.trim()?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

internal fun ArchiveDayUi.toRowModel(): ArchiveRowModel {
    val lines = buildList {
        if (myEntry != null) {
            add(entryLine(RowAuthor.You, myEntry, myPhoto != null, myVoice != null))
        } else if (myTombstone) {
            add(RowLine(RowAuthor.You, RowBody.Deleted, photo = false, voice = false))
        }
        if (partnerEntry != null) {
            add(entryLine(RowAuthor.Partner, partnerEntry, partnerPhoto != null, partnerVoice != null))
        }
    }
    return ArchiveRowModel(date, lines, holdsMyFavourite)
}

private fun entryLine(author: RowAuthor, text: String, photo: Boolean, voice: Boolean): RowLine {
    val first = firstLine(text)
    val body = when {
        first != null -> RowBody.Words(first)
        else -> RowBody.MediaOnly(photo, voice)
    }
    return RowLine(author, body, photo, voice)
}

/**
 * What a screen reader says for a row, as one string: the date, each entry, then the extras
 * ("With a photo", "Favourite"), separated by full stops. The pieces are already in the
 * reader's language; this only orders them. Example:
 * `Wednesday, 11 November. You: Tea in the garden. Ermb: Photo. Favourite.`
 */
internal fun rowDescription(date: String, entries: List<String>, extras: List<String>): String {
    val parts = (listOf(date) + entries + extras).map { it.trimEnd('.') }
    // A part that ends in an ellipsis already pauses; no full stop after it.
    return parts.foldIndexed("") { index, spoken, part ->
        when {
            index == 0 -> part
            spoken.endsWith("…") -> "$spoken $part"
            else -> "$spoken. $part"
        }
    } + if (parts.last().endsWith("…")) "" else "."
}
