package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.OggOpus
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The Ogg Opus to CAF move that lets iOS play a voice note. The fixture is half a second of
 * a 440 Hz tone made the way the backend makes a voice note (ffmpeg, libopus, 24 kbit/s,
 * mono, `voip`); the expected CAF was made from it independently and checked to decode on
 * macOS (`afinfo`: 0.5 s, 26 packets; `afconvert` back to WAV gives the tone).
 */
class OggOpusTest {
    @Test
    fun anOggOpusFileBecomesTheCafAppleDecodes() {
        assertContentEquals(Base64.decode(CAF), OggOpus.toCaf(Base64.decode(OGG)))
    }

    @Test
    fun theCafDeclaresOpusAt48kHzMonoWithTheCountsOfTheOgg() {
        val caf = assertNotNull(OggOpus.toCaf(Base64.decode(OGG)))
        assertEquals("caff", caf.decodeToString(0, 4))
        assertEquals("desc", caf.decodeToString(8, 12))
        assertEquals("opus", caf.decodeToString(28, 32))
        // frames per packet (20 ms at 48 kHz) and one channel
        assertEquals(960, caf.int(40))
        assertEquals(1, caf.int(44))
        assertEquals("pakt", caf.decodeToString(52, 56))
        assertEquals(26L, caf.long(64), "packets")
        assertEquals(24_000L, caf.long(72), "valid frames: half a second at 48 kHz")
        assertEquals(312, caf.int(80), "priming frames: the Opus pre-skip")
    }

    @Test
    fun anythingButOggOpusIsRefusedNotGuessedAt() {
        assertNull(OggOpus.toCaf(ByteArray(0)))
        assertNull(OggOpus.toCaf("not a voice note at all, long enough to be read".encodeToByteArray()))
        val ogg = Base64.decode(OGG)
        assertNull(OggOpus.toCaf(ogg.copyOf(ogg.size - 10)), "a cut-off file")
    }

    @Test
    fun framesPerPacketComeFromTheTocByte() {
        // CELT 20 ms, one frame (config 31, code 0)
        assertEquals(960L, OggOpus.frameCount(byteArrayOf(0xF8.toByte())))
        // SILK 60 ms, two frames (config 3, code 1)
        assertEquals(5760L, OggOpus.frameCount(byteArrayOf(0x19)))
        // CELT 2.5 ms, code 3 with five frames
        assertEquals(600L, OggOpus.frameCount(byteArrayOf(0x83.toByte(), 0x05)))
        assertNull(OggOpus.frameCount(byteArrayOf(0x83.toByte())))
    }

    private fun ByteArray.int(at: Int): Int = (0 until 4).fold(0) { acc, i -> (acc shl 8) or (this[at + i].toInt() and 0xFF) }

    private fun ByteArray.long(at: Int): Long = (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (this[at + i].toLong() and 0xFF) }

    /** Shared with the iOS test that hands the CAF to AVFoundation (OggOpusDecodeTest). */
    internal companion object {
        const val OGG =
        "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" +
        "v7I4U5Qng8GpO/c4P56oRoEd85WoqRj+5Yqt"
        const val CAF =
        "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"
    }
}
