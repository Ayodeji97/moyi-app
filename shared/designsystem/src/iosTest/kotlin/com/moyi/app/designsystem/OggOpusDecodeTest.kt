@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.moyi.app.designsystem

import com.moyi.app.designsystem.platform.OggOpus
import com.moyi.app.designsystem.platform.failable
import com.moyi.app.designsystem.platform.toNSData
import kotlinx.cinterop.get
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToURL
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * On iOS itself (the simulator, under `iosSimulatorArm64Test`): the CAF that [OggOpus.toCaf]
 * makes from a backend-made Ogg Opus file is opened by `AVAudioPlayer` with the right
 * length, and Apple's own Opus decoder turns it back into the tone it was made from.
 *
 * (Playback itself is not asserted: the headless test process has no audio output, and
 * `prepareToPlay` fails there for Apple's own files too.)
 */
class OggOpusDecodeTest {
    private val caf = assertNotNull(OggOpus.toCaf(Base64.decode(OggOpusTest.OGG)))

    @Test
    fun avAudioPlayerOpensTheCafWithItsLength() {
        val player = assertNotNull(failable { AVAudioPlayer(data = caf.toNSData(), error = null) })
        assertTrue(abs(player.duration - 0.5) < 0.01, "half a second, as the Ogg's granule position says: ${player.duration}")
    }

    @Test
    fun applesOpusDecoderTurnsItBackIntoTheTone() {
        // AVAudioFile reads only from a file; this one exists for the length of the test.
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + "decode-" + NSUUID().UUIDString + ".caf")
        assertTrue(caf.toNSData().writeToURL(url, atomically = true))
        try {
            val file = assertNotNull(failable { AVAudioFile(forReading = url, error = null) })
            assertEquals(48_000.0, file.processingFormat.sampleRate)
            assertEquals(1u, file.processingFormat.channelCount)
            val buffer = assertNotNull(AVAudioPCMBuffer(pCMFormat = file.processingFormat, frameCapacity = 48_000u))
            assertTrue(file.readIntoBuffer(buffer, error = null))
            assertEquals(24_000u, buffer.frameLength, "the valid frames: pre-skip and padding trimmed")
            val samples = assertNotNull(buffer.floatChannelData)[0]!!
            val loudest = (0 until buffer.frameLength.toInt()).maxOf { abs(samples[it]) }
            // ffmpeg's sine source is at 1/8 of full scale (-18 dBFS): a decoded tone, not silence or noise.
            assertTrue(loudest in 0.08f..0.2f, "peak $loudest")
        } finally {
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
        }
    }

    @Test
    fun anUnreadableFileIsNullNotACrash() {
        assertNull(failable { AVAudioPlayer(data = "not audio".encodeToByteArray().toNSData(), error = null) })
    }
}
