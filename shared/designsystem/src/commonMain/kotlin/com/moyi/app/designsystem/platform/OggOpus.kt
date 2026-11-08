package com.moyi.app.designsystem.platform

/**
 * Moves the Opus packets of an Ogg Opus file (RFC 7845, what the backend keeps a voice
 * note as) into a Core Audio Format file (Apple's CAF, `kAudioFormatOpus`), in memory.
 *
 * Why: iOS has decoded Opus since iOS 11, but only from CAF; AVFoundation will not open
 * an Ogg container. Nothing is decoded or re-encoded here, so the sound is exactly what the
 * server made; only the wrapping changes. Pure Kotlin, so it is tested on every platform.
 *
 * The CAF holds: `desc` (48 kHz Opus, the channel count, frames per packet when every
 * packet holds the same number, else 0), `pakt` (one size per packet, and its frame count
 * when they vary, with the Opus pre-skip as priming frames and the end trimmed to the last
 * page's granule position), and `data`.
 */
object OggOpus {
    /** The CAF, or null when [ogg] is not a single-stream Ogg Opus file this can read. */
    fun toCaf(ogg: ByteArray): ByteArray? {
        val stream = readPackets(ogg) ?: return null
        val head = stream.packets.firstOrNull() ?: return null
        if (head.size < OPUS_HEAD_SIZE || !head.startsWith(OPUS_HEAD)) return null
        val channels = head[OPUS_HEAD_CHANNELS].toInt() and BYTE
        val preSkip = head.u16le(OPUS_HEAD_PRE_SKIP)
        // The second packet is OpusTags; the rest is sound.
        val audio = stream.packets.drop(2).filter { it.isNotEmpty() }
        if (channels == 0 || audio.isEmpty()) return null
        val frames = audio.map { frameCount(it) ?: return null }
        val total = frames.sum()
        // Frames to play: all decoded, less the pre-skip, ending at the last page's granule position.
        val playable = (total - preSkip).coerceAtLeast(0)
        val valid = (stream.lastGranule - preSkip).coerceIn(0, playable)
        val remainder = playable - valid
        val constant = frames.distinct().singleOrNull() ?: 0L

        val out = Bytes()
        out.ascii("caff")
        out.u16(1)
        out.u16(0)

        out.chunk("desc") {
            f64(SAMPLE_RATE)
            ascii("opus")
            u32(0)
            u32(0) // bytes per packet: variable
            u32(constant)
            u32(channels.toLong())
            u32(0) // bits per channel: compressed
        }
        out.chunk("pakt") {
            u64(audio.size.toLong())
            u64(valid)
            u32(preSkip.toLong())
            u32(remainder)
            audio.forEachIndexed { i, packet ->
                varint(packet.size.toLong())
                if (constant == 0L) varint(frames[i])
            }
        }
        out.chunk("data") {
            u32(0) // edit count
            audio.forEach { bytes(it) }
        }
        return out.toByteArray()
    }

    /**
     * Frames in one Opus packet at 48 kHz (RFC 6716 §3.1): its TOC byte gives the frame
     * size and the frame count code; code 3 says the count in the next byte.
     */
    internal fun frameCount(packet: ByteArray): Long? {
        if (packet.isEmpty()) return null
        val toc = packet[0].toInt() and BYTE
        val config = toc shr 3
        val frameSize = when {
            config < 12 -> SILK_SIZES[config and 3]
            config < 16 -> HYBRID_SIZES[config and 1]
            else -> CELT_SIZES[config and 3]
        }
        val count = when (toc and 3) {
            0 -> 1
            1, 2 -> 2
            else -> if (packet.size < 2) return null else (packet[1].toInt() and 0x3F)
        }
        return frameSize.toLong() * count
    }

    private class Stream(val packets: List<ByteArray>, val lastGranule: Long)

    /** The packets of the first logical stream, rebuilt from the pages' lacing. */
    private fun readPackets(ogg: ByteArray): Stream? {
        val packets = mutableListOf<ByteArray>()
        var current = Bytes()
        var at = 0
        var serial: Long? = null
        var granule = 0L
        while (at < ogg.size) {
            if (ogg.size - at < PAGE_HEADER || !ogg.startsWith(OGG_S, at)) return null
            val pageSerial = ogg.u32le(at + SERIAL_AT)
            val segments = ogg[at + SEGMENTS_AT].toInt() and BYTE
            var body = at + PAGE_HEADER + segments
            if (body > ogg.size) return null
            if (serial == null) serial = pageSerial
            val ours = pageSerial == serial
            for (i in 0 until segments) {
                val length = ogg[at + PAGE_HEADER + i].toInt() and BYTE
                if (body + length > ogg.size) return null
                if (ours) {
                    current.bytes(ogg, body, length)
                    if (length < LACING_MAX) {
                        packets += current.toByteArray()
                        current = Bytes()
                    }
                }
                body += length
            }
            val pageGranule = ogg.u64le(at + GRANULE_AT)
            if (ours && pageGranule >= 0) granule = pageGranule
            at = body
        }
        return Stream(packets, granule)
    }

    private fun ByteArray.startsWith(prefix: ByteArray, at: Int = 0): Boolean =
        size >= at + prefix.size && prefix.indices.all { this[at + it] == prefix[it] }

    private fun ByteArray.u16le(at: Int): Int = (this[at].toInt() and BYTE) or ((this[at + 1].toInt() and BYTE) shl 8)

    private fun ByteArray.u32le(at: Int): Long = (0 until 4).fold(0L) { acc, i -> acc or ((this[at + i].toLong() and 0xFF) shl (8 * i)) }

    private fun ByteArray.u64le(at: Int): Long = (0 until 8).fold(0L) { acc, i -> acc or ((this[at + i].toLong() and 0xFF) shl (8 * i)) }

    /** A growing big-endian byte buffer (CAF is big-endian throughout). */
    private class Bytes {
        private var buffer = ByteArray(INITIAL)
        private var size = 0

        private fun room(more: Int) {
            if (size + more <= buffer.size) return
            var next = buffer.size * 2
            while (next < size + more) next *= 2
            buffer = buffer.copyOf(next)
        }

        fun byte(value: Int) {
            room(1)
            buffer[size++] = value.toByte()
        }

        fun bytes(source: ByteArray, from: Int = 0, length: Int = source.size) {
            room(length)
            source.copyInto(buffer, size, from, from + length)
            size += length
        }

        fun ascii(text: String) = text.forEach { byte(it.code) }

        fun u16(value: Int) {
            byte(value shr 8)
            byte(value)
        }

        fun u32(value: Long) = (3 downTo 0).forEach { byte((value shr (8 * it)).toInt()) }

        fun u64(value: Long) = (7 downTo 0).forEach { byte((value shr (8 * it)).toInt()) }

        fun f64(value: Double) = u64(value.toRawBits())

        /** CAF's variable-length integer: seven bits a byte, most significant first, the high bit on all but the last. */
        fun varint(value: Long) {
            var groups = 1
            while (groups < MAX_VARINT && value ushr (7 * groups) != 0L) groups++
            for (g in groups - 1 downTo 0) {
                val seven = ((value ushr (7 * g)) and 0x7F).toInt()
                byte(if (g == 0) seven else seven or 0x80)
            }
        }

        /** A chunk: its type, its size as a 64-bit count, then what [body] writes. */
        fun chunk(type: String, body: Bytes.() -> Unit) {
            val content = Bytes().apply(body)
            ascii(type)
            u64(content.size.toLong())
            bytes(content.buffer, 0, content.size)
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)

        private companion object {
            const val INITIAL = 1024
            const val MAX_VARINT = 10
        }
    }

    private val OGG_S = "OggS".encodeToByteArray()
    private val OPUS_HEAD = "OpusHead".encodeToByteArray()
    private const val PAGE_HEADER = 27
    private const val GRANULE_AT = 6
    private const val SERIAL_AT = 14
    private const val SEGMENTS_AT = 26
    private const val LACING_MAX = 255
    private const val OPUS_HEAD_SIZE = 19
    private const val OPUS_HEAD_CHANNELS = 9
    private const val OPUS_HEAD_PRE_SKIP = 10
    private const val BYTE = 0xFF

    /** Opus always decodes at 48 kHz, whatever rate it was made from (RFC 7845 §5.1). */
    private const val SAMPLE_RATE = 48_000.0
    private val SILK_SIZES = intArrayOf(480, 960, 1920, 2880)
    private val HYBRID_SIZES = intArrayOf(480, 960)
    private val CELT_SIZES = intArrayOf(120, 240, 480, 960)
}
