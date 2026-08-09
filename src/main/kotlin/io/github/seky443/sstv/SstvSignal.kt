package io.github.seky443.sstv

/**
 * A fully encoded SSTV transmission held as 16-bit signed mono PCM, plus the timeline metadata
 * needed to map a playback position back onto a scan line.
 *
 * The timeline mapping is what lets a caller paint a picture in step with the audio it is playing,
 * without ever having to decode the audio back.
 */
public class SstvSignal(
    /** 16-bit signed mono samples, in transmission order. */
    public val pcm: ShortArray,
    /** Samples per second the [pcm] is meant to be played back at. */
    public val sampleRate: Int,
    /** The mode the picture was encoded as. */
    public val mode: SstvMode,
    /** Length of the leader plus VIS header that precedes the first scan line. */
    public val headerSeconds: Double,
    /** Silence appended after the last line so a receiver sees a clean frame boundary. */
    public val trailingSilenceSeconds: Double
) {
    /** Time on air for one scan line, including sync and separator overhead. */
    public val lineSeconds: Double get() = mode.lineSeconds

    /** Number of scan lines in the transmission. */
    public val lineCount: Int get() = mode.height

    /** Length of the whole transmission, in seconds. */
    public val totalSeconds: Double get() = pcm.size.toDouble() / sampleRate

    /** Length of the whole transmission, in mono sample frames. */
    public val frameCount: Int get() = pcm.size

    /**
     * The scan line being transmitted at [seconds] into the transmission, or null during the
     * header and the trailing silence when no picture data is on air.
     */
    public fun lineIndexAt(seconds: Double): Int? {
        val intoImage = seconds - headerSeconds
        if (intoImage < 0) return null
        val index = (intoImage / lineSeconds).toInt()
        return if (index in 0 until lineCount) index else null
    }

    /**
     * How far the transmission has progressed through the picture, in fractional scan lines.
     * Returns 0 during the header and [lineCount] once the picture is complete.
     */
    public fun linePositionAt(seconds: Double): Float {
        val intoImage = seconds - headerSeconds
        if (intoImage <= 0) return 0f
        return (intoImage / lineSeconds).toFloat().coerceAtMost(lineCount.toFloat())
    }

    /**
     * The samples as little-endian 16-bit bytes — the layout every PCM sink expects, from a WAV
     * file to a raw audio device.
     */
    public fun toByteArray(): ByteArray {
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            val sample = pcm[i].toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}