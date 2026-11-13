package com.moyi.app.feature.today

import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.EntryPhotoLook
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.component.VoiceNoteLook
import com.moyi.app.feature.today.archive.ArchiveDayUi
import com.moyi.app.feature.today.archive.RowAuthor
import com.moyi.app.feature.today.archive.RowBody
import com.moyi.app.feature.today.archive.firstLine
import com.moyi.app.feature.today.archive.rowDescription
import com.moyi.app.feature.today.archive.toRowModel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Archive's compact rows: the first line of each entry, media-only entries, chips, and what is spoken. */
class ArchiveRowModelTest {
    private val date = LocalDate(2026, 11, 11)
    private val photo = EntryPhoto("p", null, 10, 10, EntryPhotoLook.Shown)
    private val voice = VoiceNote("v", null, 1000, VoiceNoteLook.Playable)

    private fun day(
        mine: String? = null,
        theirs: String? = null,
        tombstone: Boolean = false,
        myPhoto: EntryPhoto? = null,
        myVoice: VoiceNote? = null,
        theirPhoto: EntryPhoto? = null,
        favourite: Boolean = false,
    ) = ArchiveDayUi(
        date = date, myEntry = mine, myTombstone = tombstone, myEntryId = "e", partnerEntry = theirs,
        myPhoto = myPhoto, partnerPhoto = theirPhoto, myVoice = myVoice, canDelete = false,
        myMarkOnMine = favourite,
    )

    @Test
    fun firstLineIsTrimmedAndStopsAtTheFirstLineBreak() {
        assertEquals("Tea in the garden", firstLine("  Tea in the garden  \nand then the rain"))
        assertEquals("Tea", firstLine("\n\n  Tea\r\nmore"))
        assertEquals("One line", firstLine("One line"))
    }

    @Test
    fun nothingLeftAfterTrimmingIsNull() {
        assertNull(firstLine("   \n  "))
        assertNull(firstLine(""))
        assertNull(firstLine(null))
    }

    @Test
    fun mineComesFirstThenThePartnersEachWithItsFirstLine() {
        val row = day(mine = "Tea\nmore", theirs = "Soup\nmore").toRowModel()
        assertEquals(listOf(RowAuthor.You, RowAuthor.Partner), row.lines.map { it.author })
        assertEquals(listOf<RowBody>(RowBody.Words("Tea"), RowBody.Words("Soup")), row.lines.map { it.body })
    }

    @Test
    fun anEntryWithOnlyMediaShowsAMediaLineNotABlankOne() {
        val row = day(mine = "  ", myPhoto = photo, theirs = "\n", theirPhoto = photo).toRowModel()
        assertEquals(RowBody.MediaOnly(photo = true, voice = false), row.lines[0].body)
        assertEquals(RowBody.MediaOnly(photo = true, voice = false), row.lines[1].body)
        val both = day(mine = "", myPhoto = photo, myVoice = voice).toRowModel()
        assertEquals(RowBody.MediaOnly(photo = true, voice = true), both.lines.single().body)
    }

    @Test
    fun aDeletedEntryOfMineIsATombstoneLine() {
        val row = day(tombstone = true, theirs = "Soup").toRowModel()
        assertEquals(RowBody.Deleted, row.lines.first().body)
        assertEquals(RowAuthor.You, row.lines.first().author)
    }

    @Test
    fun chipsAppearOnlyForMediaThatAWordsLineCarries() {
        val withWords = day(mine = "Tea", myPhoto = photo, myVoice = voice).toRowModel()
        assertTrue(withWords.photoChip)
        assertTrue(withWords.voiceChip)
        val mediaOnly = day(mine = "", myPhoto = photo).toRowModel()
        assertFalse(mediaOnly.photoChip)
        assertFalse(day(mine = "Tea").toRowModel().photoChip)
    }

    @Test
    fun theFavouriteChipFollowsMyMarkOnly() {
        assertTrue(day(mine = "Tea", favourite = true).toRowModel().favourite)
        assertFalse(day(mine = "Tea").toRowModel().favourite)
    }

    @Test
    fun onlyARowThatDrawsThePartnersWordsAsksForCaptureProtection() {
        assertTrue(day(mine = "Tea", theirs = "Rain").toRowModel().showsPartnerWords)
        assertTrue(day(theirs = "Rain").toRowModel().showsPartnerWords)
        assertFalse(day(mine = "Tea").toRowModel().showsPartnerWords, "my words alone may be captured")
        assertFalse(day(tombstone = true).toRowModel().showsPartnerWords)
        val photoOnly = day(mine = "Tea", theirs = "  ", theirPhoto = photo).toRowModel()
        assertFalse(photoOnly.showsPartnerWords, "a Photo label holds no words")
    }

    @Test
    fun theWordsAreNeverInToString() {
        assertFalse(day(mine = "Secret words").toRowModel().toString().contains("Secret"))
    }

    @Test
    fun theSpokenRowIsTheDateThenEachEntryThenTheExtras() {
        assertEquals(
            "Wednesday, 11 November. You: Tea in the garden… Ermb: Soup. In your favourites.",
            rowDescription(
                date = "Wednesday, 11 November",
                entries = listOf("You: Tea in the garden…", "Ermb: Soup."),
                extras = listOf("In your favourites"),
            ),
        )
    }

    @Test
    fun theSpokenRowWithNothingElseIsJustTheDate() {
        assertEquals("Wednesday, 11 November.", rowDescription("Wednesday, 11 November", emptyList(), emptyList()))
    }
}
