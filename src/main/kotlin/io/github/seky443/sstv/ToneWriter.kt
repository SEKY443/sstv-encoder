package io.github.seky443.sstv

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Phase-continuous tone generator backing [SstvEncoder].
 *
 * Sample counts are derived from cumulative elapsed time rather than per-segment rounding: a
 * Martin 1 frame contains over 245,000 segments, so rounding each one independently would drift the
 * line timing far enough to shear the received picture.
 *
 * This is internal rather than nested in [SstvEncoder] so the [LineFormat] implementations can
 * write into it.
 */
internal class ToneWriter(
    private val sampleRate: Int,
    private val amplitude: Double,
    capacity: Int
) {
    private val buffer = ShortArray(capacity)
    private var written = 0
    private var elapsedSeconds = 0.0
    private var phase = 0.0

    fun tone(frequencyHz: Double, seconds: Double) {
        val count = advance(seconds)
        val phaseStep = 2.0 * PI * frequencyHz / sampleRate
        repeat(count) {
            buffer[written++] = (sin(phase) * amplitude * Short.MAX_VALUE).toInt().toShort()
            phase += phaseStep
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }
    }

    fun silence(seconds: Double) {
        val count = advance(seconds)
        repeat(count) { buffer[written++] = 0 }
        phase = 0.0
    }

    /** Emits one intensity sample on the 1500 Hz (black) to 2300 Hz (white) sub-carrier. */
    fun level(value: Int, seconds: Double) {
        tone(SstvEncoder.frequencyFor(value), seconds)
    }

    /** Returns how many samples this segment gets, keeping the timeline drift-free. */
    private fun advance(seconds: Double): Int {
        elapsedSeconds += seconds
        val target = (elapsedSeconds * sampleRate).roundToInt().coerceAtMost(buffer.size)
        return (target - written).coerceAtLeast(0)
    }

    fun finish(): ShortArray = if (written == buffer.size) buffer else buffer.copyOf(written)
}
